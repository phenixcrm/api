package com.ameriglide.phenix.model;

import jakarta.servlet.http.HttpServletRequest;
import net.inetalliance.potion.query.Query;

import static net.inetalliance.funky.StringFun.isEmpty;

public interface Searchable<T>
    extends Listable<T> {

  /**
   * Search query parameter
   */
  String parameter = "q";

  static <T> Query<T> $(final Query<T> query, final Searchable<T> searchable,
      final HttpServletRequest request) {
    final String search = request.getParameter(Searchable.parameter);
    return isEmpty(search) ? query : query.and(searchable.search(search));
  }

  Query<T> search(final String query);
}
