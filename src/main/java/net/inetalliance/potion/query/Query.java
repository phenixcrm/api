package net.inetalliance.potion.query;

import com.ameriglide.phenix.core.Functions;
import com.ameriglide.phenix.core.Iterables;
import com.ameriglide.phenix.core.Strings;
import net.inetalliance.potion.annotations.Persistent;
import net.inetalliance.potion.info.Info;
import net.inetalliance.potion.info.Property;
import net.inetalliance.potion.info.SingleColumnProperty;
import net.inetalliance.potion.jdbc.SqlQuery;
import net.inetalliance.sql.*;

import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static java.util.Set.of;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;
import static net.inetalliance.potion.Locator.count;
import static net.inetalliance.sql.OrderBy.Direction.ASCENDING;

public class Query<T>
		implements Predicate<T> {

	public final Class<T> type;
	protected final BiFunction<Namer, String, Where> where;
	protected final Predicate<T> test;
	private final String source;

	public Query(final Class<T> type, final Predicate<T> test,
	             final BiFunction<Namer, String, Where> where) {
		this(type, null, test, where);

	}

	public Query(final Class<T> type, final String source, final Predicate<T> test,
	             final BiFunction<Namer, String, Where> where) {
		this.type = type;
		this.source = source;
		this.where = where;
		this.test = test;
	}

	public static <T> Query<T> all(final Class<T> type) {
		return new Query<>(type, "Query.all", t -> true, (namer, table) -> null);
	}

	public static <T> Query<T> none(final Class<T> type) {
		return new Query<>(type, "Query.none", t -> false,
				(namer, table) -> Info.$(type).noneWhere.apply(namer));
	}

	public static <T, V extends Comparable<V>> Query<T> lt(final Class<T> type, final String property,
	                                                       final V value) {
		return new ComparisonQuery<>(type, "<", property, value,
				(a, b) -> a == null ? b != null : (b != null && a.compareTo(b) < 0),
				(namer, table) -> Where.lt(table, property, value));
	}

	public static <T, V extends Comparable<V>> Query<T> lte(final Class<T> type,
	                                                        final String property, final V value) {
		return new ComparisonQuery<>(type, "<=", property, value,
				(a, b) -> a == null || b == null || a.compareTo(b) <= 0,
				(namer, table) -> Where.lte(table, property, value));
	}

	public static <T, V extends Comparable<V>> Query<T> gt(final Class<T> type, final String property,
	                                                       final V value) {
		return new ComparisonQuery<>(type, ">", property, value,
				(a, b) -> a != null && (b == null || a.compareTo(b) > 0),
				(namer, table) -> Where.gt(table, property, value));
	}

	public static <T, V extends Comparable<V>> Query<T> gte(final Class<T> type,
	                                                        final String property, final V value) {
		return new ComparisonQuery<>(type, ">=", property, value,
				(a, b) -> a == null ? b == null : (b == null || a.compareTo(b) >= 0),
				(namer, table) -> Where.gte(table, property, value));
	}

	public static <T> Query<T> inInterval(final Class<T> type, final String property,
	                                      final DateTimeInterval interval) {
		final Property<T, LocalDate> p = Info.$(type).get(property);
		return new Query<>(type, "Query.inInterval", t -> interval.contains(LocalDateTime.from(p.apply(t))),
				(namer, table) -> Where.between(table, p.getName(), interval));
	}

	public static <T> Query<T> inInterval(final Class<T> type, final String property,
	                                      final DateInterval interval) {
		final Property<T, LocalDate> p = Info.$(type).get(property);
		return new Query<>(type, "Query.inInterval", t -> interval.contains(LocalDateTime.from(p.apply(t))),
				(namer, table) -> Where.between(table, p.getName(), interval));
	}

	public static <T> Query<T> isAfter(final Class<T> type, final String property,
	                                   final LocalDateTime date) {
		final Property<T, LocalDateTime> p = Info.$(type).get(property);
		return new Query<>(type, "Query.isAfter",
				t -> ofNullable(p.apply(t)).map(d -> d.isAfter(date)).orElse(false),
				(namer, table) -> Where.gt(table, p.getName(), date));
	}

	public static <T> Query<T> isBefore(final Class<T> type, final String property,
	                                    final LocalDateTime date) {
		final Property<T, LocalDateTime> p = Info.$(type).get(property);
		return new Query<>(type, "Query.isBefore",
				t -> ofNullable(p.apply(t)).map(d -> d.isBefore(date)).orElse(false),
				(namer, table) -> Where.lt(table, p.getName(), date));
	}

	public static <T> Query<T> eq(final Class<T> type, final String property, final Object value) {
		final Property<T, ?> prop = Info.$(type).get(property);
		return new Query<T>(type, type.getName() + "." + property + "=" + value,
				t -> Objects.equals(value, prop.apply(t)),
				Functions.throwing((namer, table) -> {
					final T t = type.getDeclaredConstructor().newInstance();
					prop.field.set(t, value);
					return prop.getWhere(namer, table, t);
				}));
	}

	public static <T> Query<T> has(final Class<T> type, final String property) {
		final Property<T, ?> prop = Info.$(type).get(property);
		return new Query<>(type, t -> prop.apply(t) != null,
				(namer, table) -> Where.not(table, property, null));
	}

	public static <T> Query<T> isNotEmpty(final Class<T> type, final String property) {
		final Property<T, String> prop = Info.$(type).get(property);
		return new Query<>(type, t -> Strings.isNotEmpty(prop.apply(t)),
				(namer, table) -> Where
						.and(Where.not(table, property, null), Where.not(table, property, "")));

	}

	public static <T, U, K> Query<T> in(final Class<T> type, final String property,
	                                    final Collection<U> collection,
	                                    final Function<U, K> plucker) {
		return in(type, property, collection.stream().map(plucker).collect(toList()));

	}

	public static <T, U> Query<T> in(final Class<T> type, final String property,
	                                 final Collection<U> collection) {
		final Property<T, U> prop = Info.$(type).get(property);
		return new Query<>(type, t -> collection.contains(prop.apply(t)),
				(namer, table) -> new InCollectionWhere(table, property, collection));
	}

	public static <T> Query<T> startsWith(final Class<T> type, final String property,
	                                      final String start) {
		return startsWith(type, property, start, true);
	}

	public static <T> Query<T> startsWith(final Class<T> type, final String property,
	                                      final String start, final boolean caseSensitive) {
		final Property<T, String> prop = Info.$(type).get(property);
		final var upperCaseStart = start.toUpperCase();
		return new Query<>(type, t -> {
			final String value = prop.apply(t);
			if (value == null) {
				return false;
			}
			return caseSensitive ? value.startsWith(start)
					: value.toUpperCase().startsWith(upperCaseStart);
		}, (namer, table) -> Where.like(table, property, start + "%", caseSensitive));
	}

  public static <T> Query<T> contains(final Class<T> type, final String property,
                                  final String query, final boolean caseSensitive) {
    var q = caseSensitive ? query : query.toUpperCase();
    final Property<T,String> prop = Info.$(type).get(property);
    return new Query<>(type, t-> {
      var value = prop.apply(t);
      if(value == null) {
        return false;
      }
      var v = caseSensitive ? value : value.toUpperCase();
      return v.contains(q);

    }, (namer,table)->Where.like(table,property,"%" + query + "%",caseSensitive));
  }

	@SuppressWarnings({"unchecked"})
	private static <O> Iterable<BiConsumer<PreparedStatement, Integer>> bind(final DbVendor vendor,
	                                                                         final O object) {
		final Class<O> type = (Class<O>) object.getClass();
		return type.getAnnotation(Persistent.class) != null
				? () -> Info.$(object)
				.keys()
				.map(p -> p.setParameters(vendor, object))
				.flatMap(Iterables::stream)
				.iterator()
				: of(SingleColumnProperty.$(type).bind(vendor, object));
	}

	/**
	 * Ands together the given queries.
	 *
	 * @param type    the target object type
	 * @param queries the underlying queries
	 * @return the combined query
	 */

	public static <T> Query<T> and(final Class<T> type, final Collection<Query<? super T>> queries) {
		return new CombiningQuery<>(type, queries, "&&", t -> queries.stream().allMatch(q -> q.test(t)),
				(namer, table) -> Where.and(queries.stream()
						.map(q -> q.getWhere(namer, table))
						.filter(Objects::nonNull)
						.collect(toList())));

	}

	public static <T> Query<T> is(final T obj) {
		final Info<T> info = Info.$(obj);
		return new Query<>(info.type, obj::equals, (namer, table) -> Where.and(
				info.keys().map(p -> p.getWhere(namer, table, obj)).collect(toList())));

	}

	/**
	 * Ors together the given queries.
	 *
	 * @param type    the target object type
	 * @param queries the underyling queries
	 * @return the combined query
	 */

	public static <T> Query<T> or(final Class<T> type, final Collection<Query<? super T>> queries) {
		return new CombiningQuery<>(type, queries, "||", t -> queries.stream().anyMatch(q -> q.test(t)),
				(namer, table) -> Where.or(queries.stream()
						.map(q -> q.getWhere(namer, table))
						.filter(Objects::nonNull)
						.collect(toList())));
	}

  public static <T> Query<T> uncacheable(final Query<T> query) {
    return new DelegatingQuery<>(query,s->s,w->w,b->b){
      @Override
      public boolean isCacheable() {
        return false;
      }
    };
  }

  @Override
	public String toString() {
		return asSql(DbVendor.POSTGRES, Namer.simple).toString();
	}

	public final SqlQuery asSql(final DbVendor vendor, final Namer namer,
	                            final AggregateField... aggregateFields) {
		final String table = nameTable(namer);
		final SqlBuilder sqlBuilder = new SqlBuilder(table, null, aggregateFields);
		final Where where = getWhere(namer, table);
		if (where != null) {
			sqlBuilder.where(where);
		}
		if (aggregateFields.length == 0) {
			sqlBuilder.orderBy(getOrderBy(table));
		}
		Sql sql = sqlBuilder.getSql();
		final List<Object> parameters = new ArrayList<>(Arrays.asList(sql.getParameters()));
		int i = 0;
		for (final Object o : build(sqlBuilder, namer, vendor, table)) {
			parameters.add(i++, o);
		}
		sql = sqlBuilder.getSql();
		final List<BiConsumer<PreparedStatement, Integer>> params =
				parameters.isEmpty() ? Collections.emptyList() : new ArrayList<>(parameters.size());
		for (final Object parameter : parameters) {
			if (parameter != null) {
				for (final BiConsumer<PreparedStatement, Integer> binding : bind(vendor, parameter)) {
					params.add(binding);
				}
			}
		}
		return new SqlQuery(sql.getQuery(), params);
	}

	protected String nameTable(final Namer namer) {
		return namer.name(type);
	}

	/**
	 * Returns a sql where clause that will return true when this query is true.
	 *
	 * @param namer a naming scheme
	 * @param table the underlying relation
	 * @return the sql where clause
	 */
	public final Where getWhere(final Namer namer, final String table) {
		return where.apply(namer, table);
	}

	public Iterable<OrderBy> getOrderBy(final String table) {
		return of();
	}

	public Iterable<Object> build(final SqlBuilder sql, final Namer namer, final DbVendor vendor,
	                              final String table) {
		return of();
	}

	@SuppressWarnings("unchecked")
	public Query<T> and(final Query<? super T> query) {

		return (Query<T>) and(type, (Set) Set.<Query>of(query, this));
	}

	@Override
	public final boolean test(final T t) {
		return test.test(t);
	}

	/**
	 * Returns a negated version of this query.
	 */
	@Override
	public Query<T> negate() {
		return new DelegatingQuery<>(this, s -> "!" + s, w -> {
			if (w instanceof NegatableWhere) {
				return ((NegatableWhere) w).negate();
			}
			throw new IllegalStateException("Cannot negate a query with a non-negatable where clause");
		}, t -> !t);
	}

	public Class[] getTypeDependencies() {
		return new Class[]{type};
	}

	public boolean isKeysOnly() {
		return false;
	}

	public <O> Query<O> join(final Class<O> oType, final String oProp) {
		final Class<T> tType = this.type;
		return new Query<>(oType, x -> true, (namer, oTable) -> {
			// make O's property match T's key columns
			Property<O, T> oProperty = Info.$(oType).get(oProp);
			if (oProperty == null) {
				throw new IllegalArgumentException("Column %s was not found on %s".formatted(oProp, oType.getSimpleName()));
			}
			var tTable = namer.name(tType);
			var tKeys = Info.$(tType)
					.keys()
					.map(Property::getColumns)
					.flatMap(Iterables::stream)
					.map(c -> c.name).iterator();
			var baseWhere = getWhere(namer, tTable);
			return Where.and(Stream.concat(Stream.of(baseWhere), Iterables.stream(oProperty.getColumns())
							.map(c -> c.name)
							.map(oColumn -> new ColumnWhere(oTable, oColumn, tTable,
									tKeys.next())))
					.filter(Objects::nonNull)
					.collect(toList()));

		});
	}

	public <O> Query<O> join(final String tProp, final Class<O> oType) {
		// T = Skill, O = AgentSkill
		final Class<T> tType = this.type;

		Property<T, O> tProperty = Info.$(tType).get(tProp);
		if (tProperty == null) {
			throw new IllegalArgumentException("Column %s was not found on %s".formatted(tProp, tType.getSimpleName()));
		}
		return new Query<>(oType, x -> true, (namer, table) -> {

			var tTable = namer.name(tType);
			var oKeys = Info.$(oType)
					.keys()
					.map(Property::getColumns)
					.flatMap(Iterables::stream)
					.map(c -> c.name).iterator();
			var oTable = namer.name(oType);
			var baseWhere = getWhere(namer, tTable);
			return Where.and(
					Stream.concat(Stream.of(baseWhere),
									Iterables.stream(tProperty.getColumns())
											.map(c -> c.name)
											.map(tColumn -> new ColumnWhere(tTable, tColumn, oTable, oKeys.next())))
							.filter(Objects::nonNull)
							.collect(toList()));

		});

	}


	public Query<T> limit(final int limit) {
		return limit(null, limit);
	}

	public Query<T> limit(final Integer offset, final int limit) {
		return new DelegatingQuery<>(this, s -> s + "[" + offset + "," + limit + "]", identity(),
				t -> t) {
			@Override
			public Iterable<Object> build(final SqlBuilder sql, final Namer namer, final DbVendor vendor,
			                              final String table) {
				sql.limit(offset, limit);
				return super.build(sql, namer, vendor, table);
			}
		};
	}

	/**
	 * Uses a database query to check if the given po matches this predicate.  This should only be
	 * used when you are curious about one specific po.  If you have a collection of pos you are
	 * interested in, run a query and check containsAll().
	 *
	 * @param obj the po to check
	 * @return true if the po matches this predicate, false otherwise
	 */
	public boolean matchesWithQuery(final T obj) {
		return count(and(type, of(this, is(obj)))) > 0;
	}

	/**
	 * Very complex queries need to use row counts to do count queries.
	 */
	public boolean isComplex() {
		return false;
	}

	/**
	 * Returns a unique source to identify the origin of this query
	 *
	 * @return defaults to class name
	 */
	public final String getQuerySource() {
		return source == null ? getClass().getName() : source;
	}

	@SuppressWarnings("unchecked")
	public Query<T> or(final Query<? super T> query) {
		return (Query<T>) or(type, Set.of(this, query));
	}

	public SortedQuery<T> orderBy(final String name) {
		return orderBy(name, ASCENDING, true);
	}

	public SortedQuery<T> orderBy(final String name, final OrderBy.Direction direction,
	                              final boolean useTableName) {
		return new SortedQuery<>(this, s -> String.format("%s-%s[%s]", s, name, direction.name())) {
			@Override
			public Iterable<OrderBy> getOrderBy(final String table) {
				return () -> concat(Iterables.stream(this.delegate.getOrderBy(table)),
						Stream.of(new OrderBy(useTableName ? table : null, name, direction))).iterator();
			}

		};
	}

	public SortedQuery<T> orderBy(final String name, final OrderBy.Direction direction) {
		return orderBy(name, direction, true);
	}
  public boolean isCacheable() {
    return true;
  }
}
