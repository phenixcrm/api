package com.ameriglide.phenix.model;

import jakarta.servlet.http.HttpServletRequest;
import net.inetalliance.potion.query.Query;
import net.inetalliance.potion.query.SortedQuery;
import net.inetalliance.sql.OrderBy;

import static net.inetalliance.sql.OrderBy.Direction.ASCENDING;
import static net.inetalliance.sql.OrderBy.Direction.DESCENDING;

public interface Sortable<T>
    extends Listable<T> {

  /**
   * Ascending query parameter
   */
  String ascending = "asc";

  /**
   * Descending query parameter
   */
  String descending = "desc";

  SortedQuery<T> orderBy(final Query<T> query, final String column,
      final OrderBy.Direction direction);

  Query<T> defaultOrder(final Query<T> query);

  final class Impl {

    public static <T> Query<T> $(final Query<T> query, final Sortable<T> sortable,
        final HttpServletRequest request) {
      final String asc = request.getParameter(Sortable.ascending);
      if (asc == null) {
        final String desc = request.getParameter(Sortable.descending);
        if (desc == null) {
          return sortable.defaultOrder(query);
        } else {
          return sortable.orderBy(query, desc, DESCENDING);
        }
      } else {
        return sortable.orderBy(query, asc, ASCENDING);
      }
    }
  }
}
