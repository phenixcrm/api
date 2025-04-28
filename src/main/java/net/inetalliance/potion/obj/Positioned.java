package net.inetalliance.potion.obj;

import static net.inetalliance.sql.OrderBy.Direction.ASCENDING;

import net.inetalliance.potion.Locator;
import net.inetalliance.potion.query.Query;
import net.inetalliance.potion.query.SortedQuery;
import net.inetalliance.sql.Aggregate;
import net.inetalliance.sql.Where;

public interface Positioned {

  String property = "position";

  static <P extends Positioned> Integer getMax(final Query<P> query) {
    return Locator.$$(query, Aggregate.MAX, Integer.class, "position");
  }

  static <P extends Positioned> SortedQuery<P> byPosition(final Class<P> type) {
    return orderBy(Query.all(type));
  }

  static <P extends Positioned> SortedQuery<P> orderBy(final Query<P> query) {
    return query.orderBy(property, ASCENDING);
  }

  static <P extends Positioned> Query<P> withPosition(final Class<P> type, final Integer position) {
    return Query.eq(type, property, position);
  }

  /**
   * Returns a query for elements between the two positions (EXCLUSIVE)
   */
  @SuppressWarnings("unchecked")
  static <P extends Positioned> Query<P> between(final P min, final P max) {
    return between((Class<P>) min.getClass(), min.getPosition(), max.getPosition());
  }

  /**
   * Returns a query for elements between the two positions (EXCLUSIVE)
   */
  static <P extends Positioned> Query<P> between(final Class<P> type, final int min,
      final int max) {
    return new Query<>(type, t -> {
      final int pos = t.getPosition();
      return min < pos && pos < max;
    }, (namer, table) -> Where.between(table, property, min, max));
  }

  int getPosition();

  void setPosition(final int position);
}
