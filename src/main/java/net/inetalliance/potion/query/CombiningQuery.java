package net.inetalliance.potion.query;

import com.ameriglide.phenix.core.Iterables;
import net.inetalliance.sql.*;

import java.util.Collection;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CombiningQuery<T>
    extends Query<T> {

  protected final Collection<Query<? super T>> queries;

  protected CombiningQuery(Class<T> type, Collection<Query<? super T>> queries, String srcDelimiter,
      Predicate<T> test,
      BiFunction<Namer, String, Where> where) {
    super(type,
        queries.stream().map(Query::getQuerySource).collect(Collectors.joining(srcDelimiter)), test,
        where);
    this.queries = queries;
  }

  @Override
  public Iterable<OrderBy> getOrderBy(final String table) {
    return () -> queries.stream().map(q -> q.getOrderBy(table)).flatMap(Iterables::stream).iterator();
  }

  @Override
  public Iterable<Object> build(SqlBuilder sql, Namer namer, DbVendor vendor, String table) {
    return () -> queries.stream().map(q -> q.build(sql, namer, vendor, table))
        .flatMap(Iterables::stream).iterator();

  }

  @Override
  public boolean isComplex() {
    return queries.stream().anyMatch(Query::isComplex);
  }

  @Override
  public boolean isCacheable() {
    return queries.stream().allMatch(Query::isCacheable);
  }
}
