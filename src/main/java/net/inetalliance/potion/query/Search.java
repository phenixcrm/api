package net.inetalliance.potion.query;

import net.inetalliance.potion.annotations.Searchable;
import net.inetalliance.potion.info.Info;
import net.inetalliance.potion.info.Property;
import net.inetalliance.sql.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import static net.inetalliance.sql.OrderBy.Direction.DESCENDING;

public class Search<T>
    extends SortedQuery<T> {

  public Search(final Class<T> type, String... terms) {
    this(type, null, Arrays.asList(terms));
  }

  public Search(final Class<T> type, final Integer limit, final Collection<String> terms) {
    super(new Query<>(type, t -> {
      for (final Property<T, ?> property : Info.$(type).properties) {
        final Searchable annotation = property.field.getAnnotation(Searchable.class);
        if (annotation != null) {
          final Object value = property.apply(t);
          if (value != null) {
            final String string = value.toString();
            for (final String term : terms) {
              if (string.contains(term)) {
                return true;
              }
            }
          }
        }
      }
      return false;

    }, (namer, table) -> new SearchWhere(table, "document", String.format("%s_query", table))) {
      @Override
      public Iterable<OrderBy> getOrderBy(final String table) {
        return Collections
            .singleton(new OrderBy(null, String.format("%s_rank", table), DESCENDING));
      }

      @Override
      public Iterable<Object> build(final SqlBuilder sql, final Namer namer, final DbVendor vendor,
          final String table) {
        sql.addColumn("*");
        sql.addColumn(null,
            String.format("ts_rank(%s.document,%s_query) AS %s_rank", vendor.escapeEntity(table),
                table,
                table));
        sql.alias(String.format("%s_query", table),
            new Sql("to_tsquery('simple',?)", true,
                (Object[]) new CharSequence[]{String.join("&", terms)}));
        if (limit != null) {
          sql.limit(limit);
        }
        return Set.of(String.join("&", terms));
      }

    }, s -> s + "?" + limit);
  }

  public Search(final Class<T> type, final Integer limit, String... terms) {
    this(type, limit, Arrays.asList(terms));
  }
}
