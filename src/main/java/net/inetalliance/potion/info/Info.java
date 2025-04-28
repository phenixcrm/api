package net.inetalliance.potion.info;

import com.ameriglide.phenix.core.*;
import net.inetalliance.potion.Locator;
import net.inetalliance.potion.Version;
import net.inetalliance.potion.annotations.*;
import net.inetalliance.potion.jdbc.SqlQuery;
import net.inetalliance.potion.obj.Positioned;
import net.inetalliance.sql.*;
import net.inetalliance.types.annotations.SubObject;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonList;
import net.inetalliance.types.json.JsonMap;
import net.inetalliance.types.localized.LocalizedObject;
import net.inetalliance.util.security.auth.Authorized;
import net.inetalliance.validation.Validator;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.ameriglide.phenix.core.Functions.memoize;
import static com.ameriglide.phenix.core.Strings.isNotEmpty;
import static java.lang.String.format;
import static java.lang.String.join;
import static java.util.Collections.emptyList;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;
import static java.util.stream.StreamSupport.stream;
import static net.inetalliance.sql.Aggregate.MIN;
import static net.inetalliance.sql.Where.exists;

public class Info<O> {

	private static final Map<Class, Info> cache;
	private static final Pattern serial = Pattern.compile("SERIAL");
	private static final Pattern quote = Pattern.compile("\"");

	static {
		cache = new HashMap<>();
	}

	public final List<Property<O, ?>> properties;
	public final List<Field> subObjectCollections;
	public final List<Field> subObjectSets;
	public final List<Field> collections;
	public final Class<O> type;
	public final boolean hasGeneratedProperties;
	public final boolean versioned;
	public final boolean locatable;
	public final boolean updateListener;
	public final boolean creationListener;
	public final Function<Namer, Where> noneWhere;
	private final List<SubObjectProperty<O, ?>> subObjectArrays;
	private final List<Field> maps;
	private final Set<String> authorizedRoles;
	private final Map<String, Property<O, ?>> byName;
	private final Function<DbVendor, CharSequence> where;

	public Info(final Class<O> type) {
		this.type = type;
		versioned = type.getAnnotation(Versioned.class) != null;
		updateListener = UpdateListener.class.isAssignableFrom(type);
		creationListener = CreationListener.class.isAssignableFrom(type);
		locatable = isLocatable(type);
		final Update update = type.getAnnotation(Update.class);
		if (!locatable || update == null) {
			authorizedRoles = Collections.emptySet();
		} else {
			authorizedRoles = new HashSet<>(Arrays.asList(update.value()));
		}
		properties = new ArrayList<>(4);
		subObjectArrays = new ArrayList<>(0);
		subObjectCollections = new ArrayList<>(0);
		subObjectSets = new ArrayList<>(0);
		collections = new ArrayList<>(0);
		maps = new ArrayList<>(0);
		byName = new TreeMap<>();
		boolean hasGeneratedProperties = false;
		for (final Class<?> superType : Iterables.assignable(type)) {
			for (final Field field : superType.getDeclaredFields()) {
				if (!Modifier.isTransient(field.getModifiers()) && !Modifier.isStatic(field.getModifiers())
						&& !"$$read".equals(
						field.getName())) {
					final Property<O, ?> property;
					if (field.getAnnotation(ForeignKey.class) != null) {
						property = ForeignProperty.$(field);
					} else if (field.getAnnotation(SubObject.class) != null) {
						if (field.getType().isArray()) {
							property = null;
							final SubObjectProperty<O, ?> arrayProperty = SubObjectProperty.$A(field);
							subObjectArrays.add(arrayProperty);
						} else if (Set.class.isAssignableFrom(field.getType())) {
							property = null;
							field.setAccessible(true);
							subObjectSets.add(field);
						} else if (Collection.class.isAssignableFrom(field.getType())) {
							property = null;
							field.setAccessible(true);
							subObjectCollections.add(field);
						} else if (Map.class.isAssignableFrom(field.getType())) {
							property = null;
							field.setAccessible(true);
							maps.add(field);
						} else {
							property = SubObjectProperty.$(field);
						}
					} else if (Collection.class.isAssignableFrom(field.getType())) {
						property = null;
						field.setAccessible(true);
						collections.add(field);
					} else if (LocalizedObject.class.isAssignableFrom(field.getType())) {
						property = SingleColumnProperty.$(field);
					} else if (Map.class.isAssignableFrom(field.getType())) {
						property = null;
						field.setAccessible(true);
						maps.add(field);
					} else {
						property = SingleColumnProperty.$(field);
					}
					hasGeneratedProperties |= field.getAnnotation(Generated.class) != null;
					if (property != null) {
						properties.add(property);
						byName.put(property.field.getName(), property);
					}
				}
			}
		}
		this.hasGeneratedProperties = hasGeneratedProperties;
		where = memoize(vendor -> " WHERE (" + properties.stream()
				.filter(Property::isKey)
				.map(Property::getColumns)
				.flatMap(Iterables::stream)
				.map(c -> c.where(vendor))
				.collect(joining(" AND ")) + ")", DbVendor.values().length);
		noneWhere = memoize(namer -> {
			final Column firstKeyColumn = keys().findFirst().orElseThrow().getColumns().iterator().next();
			return Where.eq(namer.name(type), firstKeyColumn.name, null);
		}, 1);
	}

	public static <T> boolean isLocatable(final Class<T> type) {
		final Persistent persistentAnnotation = type.getAnnotation(Persistent.class);
		return persistentAnnotation != null && persistentAnnotation.locatable();
	}

	public Stream<Property<O, ?>> keys() {
		return properties.stream().filter(Property::isKey);
	}

	public static <O> Stream<Object> keys(final O o) {
		return $(o).keys().map(k -> k.apply(o));
	}

	@SuppressWarnings({"unchecked"})
	public static <T> Info<T> $(final T t) {
		return $((Class<T>) t.getClass());
	}

	@SuppressWarnings({"unchecked"})
	public static <O> Info<O> $(final Class<O> type) {
		Info<O> poInfo = cache.get(type);
		if (poInfo == null) {
			poInfo = new Info<>(type);
			cache.put(type, poInfo);
		}
		return poInfo;
	}

	public static <O> String keysToString(final O object) {
		final Info<O> info = $(object);
		final List<Property<O, ?>> keys = info.keys().collect(toList());
		switch (keys.size()) {
			case 0:
				return "";
			case 1:
				return keys.iterator().next().apply(object).toString();
			default:
				return keys.stream().map(k -> k.apply(object).toString()).collect(joining(","));
		}
	}

	@SuppressWarnings("unchecked")
	public static <O> Comparator<O> byKeys(final Class<O> type) {
		return (o1, o2) -> $(type).keys().map(k -> {
			Comparable k1 = (Comparable) k.apply(o1);
			Comparable k2 = (Comparable) k.apply(o2);
			return k1.compareTo(k2);
		}).filter(c -> c != 0).findFirst().orElse(0);
	}

	@SuppressWarnings("unchecked")
	public static <O> boolean equalsWithKeys(final Object a, final Object b) {
		if (a == b) {
			return true;
		}
		if (a == null || b == null) {
			return false;
		}
		if (!a.getClass().equals(b.getClass())) {
			return false;
		}
		final Info<O> info = (Info<O>) $(a);
		return info.keys()
				.map((Property<O, ?> k) -> !Objects.equals(k.apply((O) a), k.apply((O) b)))
				.filter(e -> !e)
				.findFirst()
				.orElse(true);
	}

	public static <T> int hashCodeWithKeys(final T t) {
		return $(t).keys().map(k -> k.apply(t)).filter(Objects::nonNull).collect(toList()).hashCode();
	}

	private static <T> JsonList subToJsonList(final Collection<T> src) {
		final Info<T> info = Info.$(src.iterator().next());
		return toJsonList(info, src);
	}

	private static <T> JsonList toJsonList(final Info<T> info, final Collection<T> src) {
		return src.stream().map(info::toJson)
				.collect(Collectors.toCollection(() -> new JsonList(src.size())));
	}

	private static String createMtmPositionedTriggerFunction(final String tableName,
	                                                         final Iterable<Column> columns) {
		final StringBuilder wheres = new StringBuilder(32);
		for (final Column column : columns) {
			if (wheres.length() > 0) {
				wheres.append(" AND ");
			}
			wheres.append(column.name);
			wheres.append("=new.").append(column.name);
		}
		return format(
				"CREATE FUNCTION %1$s_%3$s() RETURNS trigger AS $%1$s$\n" + "BEGIN\n"
						+ "  IF NEW.%3$s IS NULL THEN\n" + "    " +
						"NEW.%3$s := (SELECT COALESCE(MAX(%3$s),0) FROM %1$s WHERE %2$s) + 1;\n" + "  END IF;\n"
						+ "  RETURN NEW;" +
						"\n" + "END;\n" + "$%1$s$ LANGUAGE plpgsql;",
				tableName, wheres, Positioned.property);
	}

	public O lookup(final String arg) {
		try {
			final O o = type.getDeclaredConstructor().newInstance();
			final Property<O, ?> keyProperty = keys().findFirst().orElseThrow();
			keyProperty.field.set(o, Classes.convert(keyProperty.type, arg));
			return Locator.$(o);
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	public Stream<Property<O, ?>> external() {
		return properties.stream().filter(Property::isExternal);
	}

	public Stream<Property<O, ?>> properties() {
		return properties.stream();
	}

	public CreationListener asCreationListener(final O o) {
		return (CreationListener) o;
	}

	@SuppressWarnings({"unchecked"})
	public UpdateListener<O> asUpdateListener(final O o) {
		return (UpdateListener<O>) o;
	}

	public void fill(final O object, final JsonMap dest) {
		Locator.read(object);
		for (final Map.Entry<String, Json> entry : dest.entrySet()) {
			final Property<O, ?> property = get(entry.getKey());
			if (entry.getValue() == null) {
				if (property instanceof SingleColumnProperty) {
					entry.setValue(property.toJson(object).get(property.getColumns().iterator().next().name));
				} else {
					throw new IllegalArgumentException(
							format("expected single object for %s but didn't find a single column property",
									entry.getKey()));
				}
			} else {
				final Object subObject = property.apply(object);
				if (subObject != null) {
					$(subObject).fill(subObject, (JsonMap) entry.getValue());
				}
			}
		}

	}

	@SuppressWarnings("unchecked")
	public <V> Property<O, V> get(final String name) {
		return (Property<O, V>) byName.get(name);
	}

	@SuppressWarnings("unchecked")
	public void fromJson(final O dest, final JsonMap src) {
		try {
			for (final Property<O, ?> property : properties) {
				if (property.containsProperty(src)) {
					if (property instanceof SubObjectProperty) {
						final Object subobject = property.type.getDeclaredConstructor().newInstance();
						final Info subInfo = $(property.type);
						subInfo.fromJson(subobject, src.getMap(property.field.getName()));
						property.field.set(dest, subobject);
					} else {
						property.setIf(dest, src);
					}
				}
			}
		} catch (final IllegalAccessException | InstantiationException | NoSuchMethodException |
		               InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}

	public List<String> getConstraints(final DbVendor vendor, final Namer namer) {
		// add foreign keys
		var table = getTableName(vendor, namer);
		return properties.stream()
				.filter(Property::isForeignKey)
				.map(p -> {
					var fk = p.getForeignKeys(vendor, namer);
					return fk == null ? null : "fk_" + p.getName() + " " + fk;
				})
				.map(fk -> "ALTER TABLE " + table + " ADD CONSTRAINT " + fk).collect(toList());

	}

	private String getTableName(final DbVendor vendor, final Namer namer) {
		return getTableName(vendor, namer, "");
	}

	private String getTableName(final DbVendor vendor, final Namer namer, final String suffix) {
		return vendor.escapeEntity(namer.name(type) + suffix);
	}

	public List<String> getCreate(final DbVendor vendor, final Namer namer) {
		final Map<String, Collection<String>> checks = Validator.instance.get().getColumnChecks(type, vendor);

		final List<String> statements = new ArrayList<>(1);
		// first create necessary enum types
		properties.stream().map(Property::getReferencedEnums).flatMap(Iterables::stream).forEach(type -> {
			final String createType = vendor.createType(type);
			if (createType != null) {
				statements.add(createType);
			}
		});
		// start creating the main table
		final StringBuilder create = new StringBuilder(32);
		create.append("CREATE TABLE ");
		final String table = getTableName(vendor, namer);
		create.append(table);
		// add the properties
		create.append(properties.stream()
				.map(Property::getColumns)
				.flatMap(Iterables::stream)
				.map(c -> c.toDefinition(vendor, checks.get(c.name)))
				.collect(joining(",\n", " (\n", "")));
		// add primary keys
		create.append(keys().map(Property::getColumns)
				.flatMap(Iterables::stream)
				.map(c -> c.name)
				.map(vendor::escapeEntity)
				.collect(joining(", ", ",\nPRIMARY KEY(", ")")));
		properties.stream()
				.filter(Property::isForeignKey)
				.map(p->p.getForeignKeys(vendor,namer))
				.forEach(fk -> create.append(",\n").append(fk));


		// create search
		final Set<String> searchables = new HashSet<>();
		for (final Property<O, ?> property : properties) {
			for (final String index : property.getSearchables(vendor)) {
				searchables.add(index);
			}
		}
		if (!searchables.isEmpty()) {
			create.append(",\ndocument tsvector");
		}
		create.append(')'); // close the property list
		statements.add(create.toString());
		if (type.getAnnotation(Versioned.class) != null) {
			final StringBuilder createVersions = new StringBuilder(32);
			final Info<Version> versionInfo = $(Version.class);
			createVersions.append("CREATE TABLE ");
			final String versionsTable = getTableName(vendor, namer, "Versions");
			createVersions.append(versionsTable);
			createVersions.append(serial.matcher(properties.stream()
					.map(Property::getColumns)
					.flatMap(Iterables::stream)
					.map(c -> c.toDefinition(vendor))
					.collect(joining(",\n", " (\n", ""))

			).replaceAll("INTEGER"));
			createVersions.append(',');
			createVersions.append(versionInfo.properties.stream()
					.map(Property::getColumns)
					.flatMap(Iterables::stream)
					.map(c -> c.toDefinition(vendor, emptyList()))
					.collect(joining(", ")));
			// add primary keys
			createVersions.append(versionInfo.keys()
					.map(Property::getColumns)
					.flatMap(Iterables::stream)
					.map(c -> c.name)
					.map(vendor::escapeEntity)
					.collect(joining(",", ",\nPRIMARY KEY(", ")")));
			createVersions.append(')');
			statements.add(createVersions.toString());
		}
		if (Positioned.class.isAssignableFrom(type)) {
			statements.add(createPositionedTriggerFunction(table));
			statements.add(createPositionedTrigger(table));
		}
		// create indexes
		for (final Property<O, ?> property : properties) {
			for (final String index : property.getIndexes(vendor, quote.matcher(table).replaceAll(""))) {
				statements.add(index);
			}
		}
		if (!searchables.isEmpty()) {
			statements.add(
					format("CREATE INDEX %s ON %s USING GIN(document)",
							getTableName(vendor, namer, "_document"), table));
			statements.add("CREATE FUNCTION " + getTableName(vendor, namer,
					"_updateDocument()") + " RETURNS trigger AS $$\nbegin\n new" +
					".document :=\n" + String
					.join("||\n", searchables) + ";\n RETURN NEW;\nEND\n $$ LANGUAGE plpgsql;\n");
			statements.add("CREATE TRIGGER " + getTableName(vendor, namer,
					"_updateDocument") + "\n" + "BEFORE INSERT OR UPDATE ON\n" + table
					+ "\nFOR EACH ROW EXECUTE PROCEDURE " + getTableName(
					vendor, namer, "_updateDocument()") + ";");
		}

		return statements;
	}

	private static String createPositionedTriggerFunction(final String tableName) {
		return format(
				"CREATE FUNCTION %1$s_%2$s() RETURNS trigger AS $%1$s$\n" + "BEGIN\n"
						+ "  IF NEW.%2$s IS NULL THEN\n" + "    " +
						"NEW.%2$s := (SELECT COALESCE(MAX(%2$s),0) FROM %1$s) + 1;\n" + "  END IF;\n"
						+ "  RETURN NEW;\n" + "END;" +
						"\n" + "$%1$s$ LANGUAGE plpgsql;",
				tableName, Positioned.property);
	}

	private static String createPositionedTrigger(final String tableName) {
		return format(
				"CREATE TRIGGER %1$s_%2$s BEFORE INSERT OR UPDATE ON %1$s "
						+ "FOR EACH ROW EXECUTE PROCEDURE %1$s_%2$s();",
				tableName, Positioned.property);
	}

	public Stream<Property<O, ?>> manyToMany() {
		return properties.stream().filter(Property::isManyToMany);
	}

	public SqlQuery getDelete(final DbVendor vendor, final Namer namer, final O o) {
		final StringBuilder delete = new StringBuilder(32);
		delete.append("DELETE FROM ");
		delete.append(getTableName(vendor, namer));
		// attach wheres for key columns
		return withWhere(vendor, delete, o);
	}

	private SqlQuery withWhere(final DbVendor vendor, final StringBuilder sql, final O o) {
		return withWhere(vendor, sql, null, o);
	}

	private SqlQuery withWhere(final DbVendor vendor, final StringBuilder sql,
	                           final String postClause, final O o) {
		sql.append(where.apply(vendor));
		if (isNotEmpty(postClause)) {
			sql.append(postClause);
		}
		return new SqlQuery(sql.toString(),
				() -> keys().map(p -> p.setParameters(vendor, o)).flatMap(Iterables::stream).iterator());

	}

	public SqlQuery getDeleteVersion(final DbVendor vendor, final Namer namer, final String author,
	                                 final O o) {
		return getVersionQuery(vendor, namer, new Version(author, LocalDateTime.now(), true, false), o,
				Property::isKey);
	}

	private SqlQuery getVersionQuery(final DbVendor vendor, final Namer namer, final Version version,
	                                 final O o,
	                                 final Predicate<? super Property<O, ?>> included) {
		final StringBuilder insert = new StringBuilder(32);
		insert.append("INSERT INTO ");
		insert.append(getTableName(vendor, namer, "Versions"));
		final Info<Version> versionInfo = $(version);

		final Iterable<Property<Version, ?>> versionProperties =
				() -> versionInfo.properties.stream().filter(p -> !p.isGenerated()).iterator();

		final Iterable<Property<O, ?>> includedProperties = () -> properties.stream().filter(included)
				.iterator();

		final List<String> columns =
				concat(stream(versionProperties.spliterator(), false),
						stream(includedProperties.spliterator(), false)).map(
								Property::getColumns)
						.flatMap(
								c -> stream(
										c.spliterator(),
										false))
						.map(
								c -> c.name)
						.map(
								vendor::escapeEntity)
						.collect(
								toList());

		insert.append('(');
		insert.append(join(",", columns));
		insert.append(") VALUES (");
		insert.append(columns.stream().map(c -> "?").collect(joining(",")));
		insert.append(')');

		return new SqlQuery(insert.toString(),
				concat(Iterables.stream(versionProperties).map(p -> p.setParameters(vendor, version)),
						Iterables.stream(includedProperties).map(p -> p.setParameters(vendor, o))).flatMap(
						Iterables::stream).collect(toList()));

	}

	@SuppressWarnings({"unchecked"})
	public String getDrop(final DbVendor vendor, final Namer namer) {
		final StringBuilder drop = new StringBuilder(64);
		drop.append(format("DROP TABLE IF EXISTS %s CASCADE;\n", getTableName(vendor, namer)));
		return drop.toString();
	}

	public SqlQuery getInsert(final DbVendor vendor, final Namer namer, final O o) {
		final StringBuilder insert = new StringBuilder(32);
		insert.append("INSERT INTO ");
		insert.append(vendor.escapeEntity(getTableName(vendor, namer)));
		// column names
		final Predicate<Property<?, ?>> notGenerated = not(Property::isGenerated);
		insert.append(properties.stream()
				.filter(p -> !p.isGenerated())
				.map(Property::getColumns)
				.flatMap(Iterables::stream)
				.map(c -> c.name)
				.map(vendor::escapeEntity)
				.collect(joining(",", "(", ")")));
		// values
		insert.append(properties.stream()
				.filter(p -> !p.isGenerated())
				.map(Property::getColumns)
				.flatMap(Iterables::stream)
				.map(c -> "?")
				.collect(joining(",", " VALUES (", ")")));

		// get back any auto-generated fields
		if (hasGeneratedProperties) {
			insert.append(properties.stream()
					.filter(Property::isGenerated)
					.map(Property::getColumns)
					.flatMap(Iterables::stream)
					.map(c -> c.name)
					.map(vendor::escapeEntity)
					.collect(joining(",", " RETURNING ",  "")));
		}

		return new SqlQuery(insert.toString(), () -> properties.stream()
				.filter(notGenerated)
				.map(p -> p.setParameters(vendor, o))
				.flatMap(Iterables::stream)
				.iterator());
	}


	public SqlQuery getInsertVersion(final DbVendor vendor, final Namer namer, final String author,
	                                 final O o) {
		return getVersionQuery(vendor, namer, new Version(author, LocalDateTime.now(), false, false), o,
				Property::isKey);
	}

	public SqlQuery getSelect(final DbVendor vendor, final Namer namer, final O o) {
		final StringBuilder select = new StringBuilder(32);
		select.append("SELECT * FROM ");
		select.append(vendor.escapeEntity(getTableName(vendor, namer)));
		return withWhere(vendor, select, o);
	}

	public SqlQuery getSelectVersion(final DbVendor vendor, final Namer namer, final O o,
	                                 final Version v) {
		final Info<Version> versionInfo = $(Version.class);
		final String select = "SELECT " + properties.stream()
				.map(Property::getColumns)
				.flatMap(Iterables::stream)
				.map(c -> c.name)
				.map(vendor::escapeEntity)
				.collect(joining(",")) + " FROM " + vendor.escapeEntity(
				getTableName(vendor, namer, "Versions")) + where.apply(vendor)
				+ "AND version > ? ORDER BY version DESC";
		return new SqlQuery(select,
				() -> concat(versionInfo.properties.stream().map(p -> p.setParameters(vendor, v)),
						properties.stream().map(p -> p.setParameters(vendor, o))).flatMap(
						Iterables::stream).iterator());
	}

	public SqlQuery getUpdate(final DbVendor vendor, final Namer namer, final O old,
	                          final O updated) {
		final StringBuilder update = new StringBuilder(32);
		update.append("UPDATE ");
		update.append(vendor.escapeEntity(getTableName(vendor, namer)));
		final Predicate<Property<O, ?>> modified = Property.modified(old, updated);
		// add the list of changed values
		update.append(" SET ");
		final CharSequence set = properties.stream()
				.filter(modified)
				.map(Property::getColumns)
				.flatMap(Iterables::stream)
				.map(c -> c.where(vendor))
				.collect(joining(", "));
		if (Strings.isEmpty(set)) {
			return null;
		}
		update.append(set);
		// restrict to the old object
		update.append(where.apply(vendor));
		return new SqlQuery(update.toString(), () -> concat(
				properties.stream().filter(Property.modified(old, updated))
						.map(p -> p.setParameters(vendor, updated)),
				keys().map(p -> p.setParameters(vendor, old))).flatMap(Iterables::stream).iterator());

	}

	public List<SqlQuery> getUpdateVersion(final DbVendor vendor, final Namer namer,
	                                       final String author, final O old,
	                                       final O updated) {
		final List<SqlQuery> list = new ArrayList<>(2);
		final Predicate<Property<O, ?>> include = Property.modified(old, updated).or(Property::isKey);
		final Version version = new Version(author, LocalDateTime.now(), false, true);
		list.add(getVersionQuery(vendor, namer, version, old, include));
		list.add(getVersionQuery(vendor, namer, version, updated, include));
		return list;
	}

	public SqlQuery getVersion(final DbVendor vendor, final Namer namer, final O o,
	                           final Integer versionId) {
		final Info<Version> versionInfo = $(Version.class);
		final String select = "SELECT " + versionInfo.properties.stream()
				.map(Property::getColumns)
				.flatMap(Iterables::stream)
				.map(c -> c.name)
				.map(vendor::escapeEntity)
				.collect(joining(",")) + " FROM " + getTableName(vendor,
				namer,
				"Versions"
		) + where
				.apply(vendor) + " AND version=?";
		final Version version = new Version();
		version.version = versionId;
		return new SqlQuery(select, () -> concat(keys().map(p -> p.setParameters(vendor, o)),
				versionInfo.keys().map(p -> p.setParameters(vendor, version))).flatMap(
				Iterables::stream).iterator());
	}

	public Where getVersionCreationWhere(final DbVendor vendor, final Namer namer,
	                                     final DateInterval period) {
		final String table = getTableName(vendor, namer);
		final String versionTable = getTableName(vendor, namer, "Versions");
		final AggregateField field = new AggregateField(MIN, "modified");
		final SqlBuilder versionSql = new SqlBuilder(versionTable, null, field);
		versionSql.where(Where.between(versionTable, field.toString(), period));
		keys().map(Property::getColumns)
				.flatMap(Iterables::stream)
				.forEach(column -> versionSql
						.where(new ColumnWhere(table, column.name, versionTable, column.name)));
		return exists(versionSql.getSql());
	}

	public SqlQuery getVersions(final DbVendor vendor, final Namer namer, final O o) {
		final Info<Version> versionInfo = $(Version.class);
		final StringBuilder select = new StringBuilder(32);
		select.append("SELECT ");
		select.append(
				concat(properties.stream(), versionInfo.properties.stream()).map(Property::getColumns)
						.flatMap(
								i -> stream(i.spliterator(), false))
						.map(c -> c.name)
						.map(vendor::escapeEntity)
						.collect(joining(",")));
		select.append(" FROM ");
		select.append(getTableName(vendor, namer, "Versions"));
		return withWhere(vendor, select, " ORDER BY version DESC", o);
	}

	public boolean isAuthorized(final Authorized authorized) {
		return authorized != null && authorized.getRoles().containsAll(authorizedRoles);
	}

	public void read(final O object, final Function<String, Object> cursor) {
		try {
			for (final Property<O, ?> property : properties) {
				property.read(object, cursor);
			}
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings({"UnusedDeclaration"})
	public void readExternal(final DataInput in, final O o)
			throws IOException {
		for (final Property<O, ?> property : properties) {
			try {
				property.field.set(o, property.read(in, o));
			} catch (final IllegalAccessException e) {
				throw new PersistenceError(e);
			}
		}
	}

	public Json recordDefinition() {
		final JsonList definitions = new JsonList(properties.size());
		final Stream<Property<O, ?>> filteredProperties;
		if (Enum.class.isAssignableFrom(type)) // don't include self-referential properties on enums
		{
			filteredProperties = properties.stream().filter(p -> p.type != type);
		} else {
			filteredProperties = properties.stream();
		}
		filteredProperties.map(Property::recordDefinition).flatMap(Iterables::stream)
				.forEach(definitions::add);
		return definitions;
	}

	public JsonMap toJson(final O o) {
		return o == null ? null : toJson(o, p -> true);
	}

	public JsonMap toJson(final O o, final Predicate<? super Property> predicate) {
		final Map<String, Json> map = new HashMap<>(properties.size());
		final O canonical = locatable ? Optionals.of(Locator.$(o)).orElse(o) : o;

		properties.stream().filter(Predicates.or(predicate, Property::isKey)).map(p -> p.toJson(canonical))
				.forEach(map::putAll);
		try {
			for (final Field field : collections) {
				map.put(field.getName(), Json.Factory.$(field.get(o)));
			}
			concat(subObjectCollections.stream(), subObjectSets.stream()).forEach(field -> {
				final Collection<?> collection;
				try {
					collection = (Collection) field.get(o);
				} catch (final IllegalAccessException e) {
					throw new PersistenceError(e);
				}
				if (collection == null) {
					map.put(field.getName(), null);
				} else if (collection.isEmpty()) {
					map.put(field.getName(), JsonList.empty);
				} else {
					map.put(field.getName(), subToJsonList(collection));
				}
			});
		} catch (final IllegalAccessException e) {
			throw new PersistenceError(e);
		}
		return new JsonMap(map);
	}

	public JsonList toJsonList(final Collection<O> src) {
		return toJsonList(this, src);
	}

	public void write(final O object, final BiConsumer<String, Object> cursor) {
		for (final Property<O, ?> property : properties) {
			property.write(object, cursor);
		}
	}

	@SuppressWarnings({"UnusedDeclaration"})
	public void writeExternal(final DataOutput out, final O o)
			throws IOException {
		for (final Property<O, ?> property : properties) {
			property.write(out, o);
		}
	}

}
