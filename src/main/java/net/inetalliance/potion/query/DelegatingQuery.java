package net.inetalliance.potion.query;

import net.inetalliance.sql.*;

import java.util.function.Function;
import java.util.function.Predicate;

public class DelegatingQuery<T>
    extends Query<T> {

  final Query<T> delegate;

  public DelegatingQuery(final Query<T> delegate, Function<String, String> source,
      Function<Where, Where> where,
      Predicate<Boolean> test) {
    super(delegate.type, source.apply(delegate.getQuerySource()), t -> test.test(delegate.test(t)),
        (namer, table) -> where.apply(delegate.where.apply(namer, table)));
    this.delegate = delegate;
  }

  @Override
  protected String nameTable(final Namer namer) {
    return delegate.nameTable(namer);
  }

  @Override
  public Iterable<OrderBy> getOrderBy(final String table) {
    return delegate.getOrderBy(table);
  }

  @Override
  public Iterable<Object> build(final SqlBuilder sql, final Namer namer, final DbVendor vendor,
      final String table) {
    return delegate.build(sql, namer, vendor, table);
  }

  @Override
  public boolean isKeysOnly() {
    return delegate.isKeysOnly();
  }

  @Override
  public boolean matchesWithQuery(final T obj) {
    return delegate.matchesWithQuery(obj);
  }

  @Override
  public boolean isComplex() {
    return delegate.isComplex();
  }

  @Override
  public boolean isCacheable() {
    return delegate.isCacheable();
  }
}
