package net.inetalliance.potion.info;

import com.ameriglide.phenix.core.Iterables;
import net.inetalliance.potion.Locator;
import net.inetalliance.potion.annotations.ForeignKey;
import net.inetalliance.potion.annotations.Indexed;
import net.inetalliance.potion.annotations.PrimaryKey;
import net.inetalliance.sql.DbVendor;
import net.inetalliance.sql.Namer;
import net.inetalliance.types.annotations.Required;
import net.inetalliance.types.annotations.Unique;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static net.inetalliance.potion.annotations.Indexed.Method.BTREE;

class ForeignProperty<O, F>
		extends SubObjectProperty<O, F> {

	private ForeignProperty(final Class<F> type, final Field field) {
		super(type, field);
	}

	private ForeignProperty(final Class<F> type, final Field field, final String prefix) {
		super(type, field, prefix, field.getAnnotation(Required.class) != null,
				field.getAnnotation(Unique.class) != null);
	}

	private ForeignProperty(final Class<F> type, final Field field, final String prefix,
	                        final boolean required,
	                        final boolean unique) {
		super(type, field, prefix, required, unique);
	}

	@SuppressWarnings({"unchecked"})
	public static <O, P> SubObjectProperty<O, P> $(final Field field) {
		return new ForeignProperty<>((Class<P>) field.getType(), field);
	}

	public static <P> SubObjectProperty<P, P> $(final Class<P> type) {
		return new ForeignProperty<>(type, null, null, false, false);
	}

	@Override
	public void clone(final O src, final O dest)
			throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
		final F srcObj = apply(src);
		if (srcObj == null) {
			field.set(dest, null);
		} else {
			final F destObj = construct(dest);
			Info.$(type).keys()
					.forEach((Property<F, ?> prop) -> set(prop, destObj, srcObj));
			field.set(dest, destObj);
		}
	}

	private void set(Property<F, ?> property, F dest, F src) {
		try {
			property.field.set(dest, property.apply(src));
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Property<O, F> rename(final String prefix, final boolean required, final boolean unique,
	                             final boolean forcePrefix) {
		return new ForeignProperty<>(type, field,
				this.prefix == null ? prefix : String.format("%s_%s", prefix, this.prefix));
	}

	@Override
	public F read(final F previousValue, final O dest, final Map<String, Json> map) {
		try {
			final F newValue = construct(dest);
			final boolean sameObject = getProperties().map(property -> {
				final Object previousKey = previousValue == null ? null : property.apply(previousValue);
				final Object currentKey = read(property, previousValue, newValue, map);
				try {
					property.field.set(newValue, currentKey);
				} catch (IllegalAccessException e) {
					throw new PersistenceError(e);
				}
				return Objects.equals(previousKey, currentKey);
			}).reduce(true, (a, b) -> a && b);

			return readResolve(sameObject ? previousValue : newValue);

		} catch (Exception e) {
			throw new PersistenceError(e);
		}
	}

	@Override
	protected Stream<Property<F, ?>> getProperties() {
		return super.getProperties().filter(Property::isKey);
	}

	@Override
	public Iterable<String> getIndexes(final DbVendor vendor, final String table) {
		final PrimaryKey pk = field.getAnnotation(PrimaryKey.class);
		final Indexed index = field.getAnnotation(Indexed.class);
		if (pk != null) {
			final Set<String> pkIndexes = Collections.singleton(
					String.format("CREATE INDEX %s_%s ON %s USING %s(%s)", table, field.getName(),
							vendor.escapeEntity(table),
							BTREE, getProperties().filter(Property::isKey)
									.map(Property::getColumns)
									.flatMap(Iterables::stream)
									.map(c -> c.name)
									.map(vendor::escapeEntity)
									.collect(joining(", "))));
			return index == null ? pkIndexes :
					() -> Stream.concat(pkIndexes.stream(), Iterables.stream(super.getIndexes(vendor, table))).iterator();
		} else if (index != null) {
			return super.getIndexes(vendor, table);
		} else {
			return Collections.emptySet();
		}

	}

	@Override
	public Iterable<String> getSearchables(final DbVendor vendor) {
		return Collections.emptySet();
	}

	@Override
	public String getForeignKeys(final DbVendor vendor, final Namer namer) {
		final ForeignKey foreignKey = field.getAnnotation(ForeignKey.class);
		return "FOREIGN KEY (" + getProperties().filter(Property::isKey)
				.map(Property::getColumns)
				.flatMap(Iterables::stream)
				.map(c -> c.name)
				.map(vendor::escapeEntity)
				.collect(joining(", ")) + ") REFERENCES " + vendor.escapeEntity(
				namer.name(field.getType())) + "(" + Info.$(type)
				.keys()
				.map(Property::getColumns)
				.flatMap(Iterables::stream)
				.map(c -> c.name)
				.map(vendor::escapeEntity)
				.collect(joining(", ")) + ") ON UPDATE " + foreignKey.onUpdate()
				.toSql() + " ON DELETE " + foreignKey
				.onDelete()
				.toSql();
	}

	@Override
	public Map<String, Json> toJson(final O o) {
		final JsonMap map = new JsonMap();
		final F f = apply(o);
		Info.$(type).keys().map(k -> k.toJson(f)).forEach(map::putAll);
		return JsonMap
				.singletonMap(field.getName(), map.size() == 1 ? map.values().iterator().next() : map);
	}

	@Override
	protected F readResolve(final F f) {
		return Locator.getCanonical(f);
	}

	private <P> P read(final Property<F, P> property, final F previous, final F dest,
	                   final Map<String, Json> values) {
		final P previousValue = property.apply(previous);
		return property.read(previousValue, dest, values);
	}
}
