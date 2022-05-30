package com.ameriglide.phenix.model;

import jakarta.servlet.http.HttpServletRequest;
import net.inetalliance.potion.Locator;
import net.inetalliance.potion.query.Query;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonList;
import net.inetalliance.types.json.JsonMap;
import net.inetalliance.types.struct.pagination.PaginatedCollection;

import java.util.Collection;
import java.util.Set;

import static com.ameriglide.phenix.PhenixServlet.getParameter;
import static java.util.stream.Collectors.toList;

public interface Listable<T> {

  /**
   * Page query parameter
   */
  String page = "p";
  /**
   * Page size query parameter
   */
  String pageSize = "n";

  static <T> JsonMap $(final Class<T> type, final Listable<T> listable,
      final HttpServletRequest request) {
    Query<T> query = listable.all(type, request);
    if (listable instanceof Searchable) {
      query = Searchable.$(query, (Searchable<T>) listable, request);
    }
    if (listable instanceof Sortable) {
      query = Sortable.Impl.$(query, (Sortable<T>) listable, request);
    }
    final int total = Locator.count(query);
    final int pageSize = getParameter(request, Listable.pageSize, 0);
    final int page = getParameter(request, Listable.page, 1);
    if (pageSize > 0) {
      query = query.limit((page - 1) * pageSize, pageSize);
    }
    final Set<T> results = Locator.$$(query);
    return formatResult(total,
        results.stream().map(t -> listable.toJson(request, t)).collect(toList()), page,
        pageSize);
  }

  static JsonMap formatResult(final int total, final Collection<? extends Json> results,
      final int page,
      final int pageSize) {
    final JsonMap json = new JsonMap();
    json.put("total", total);
    if (pageSize > 0) {
      json.put("page", page);
      json.put("pageSize", pageSize);
    }
    json.put("data", new JsonList(results));
    return json;
  }

  static <T> Query<T> limit(final Query<T> query, final HttpServletRequest request) {
    final int pageSize = getParameter(request, Listable.pageSize, 0);
    final int page = getParameter(request, Listable.page, 1);
    return pageSize > 0 ? query.limit((page - 1) * pageSize, pageSize) : query;
  }

  static JsonMap paginateResult(final Collection<? extends Json> collection,
      final HttpServletRequest request) {
    final int pageSize = getParameter(request, Listable.pageSize, 0);
    if (pageSize > 0) {
      final int page = getParameter(request, Listable.page, 1);
      final int start = (page - 1) * pageSize;
      final Collection<? extends Json> paginated = PaginatedCollection
          .$(collection, start, start + pageSize);
      return formatResult(collection.size(), paginated, page, pageSize);
    } else {
      return formatResult(collection);
    }
  }

  static JsonMap formatResult(final Collection<? extends Json> results) {
    return formatResult(results.size(), results, 0, 0);
  }

  static JsonMap formatResult(final int total, final Collection<? extends Json> results) {
    return formatResult(total, results, 0, 0);
  }

  /**
   * Query to fetch all the elements of the given type
   */
  Query<T> all(final Class<T> type, final HttpServletRequest request);

  Json toJson(final HttpServletRequest request, final T t);
}
