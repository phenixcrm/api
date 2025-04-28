package net.inetalliance.potion.query;

import java.util.function.Function;
import net.inetalliance.sql.DbVendor;
import net.inetalliance.sql.Namer;
import net.inetalliance.sql.OrderBy;
import net.inetalliance.sql.SqlBuilder;

public class Join<A, T>
    extends Query<T> {

  private final Query<A> query;

  public Join(final Query<A> query, final Class<T> join, final Function<T, A> functor) {
    super(join, query.getQuerySource() + "-" + functor.getClass().getName(),
        t -> query.test(functor.apply(t)),
        (namer, table) -> query.getWhere(namer, namer.name(query.type)));
    this.query = query;
  }

  @Override
  public Iterable<OrderBy> getOrderBy(final String table) {
    return query.getOrderBy(table);
  }

  @Override
  public Iterable<Object> build(final SqlBuilder sql, final Namer namer, final DbVendor vendor,
      final String table) {
    return query.build(sql, namer, vendor, namer.name(query.type));
  }

}
