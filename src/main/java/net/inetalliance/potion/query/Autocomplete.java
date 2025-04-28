package net.inetalliance.potion.query;

import com.ameriglide.phenix.core.Iterables;
import net.inetalliance.potion.annotations.Autocompletable;
import net.inetalliance.potion.info.Info;
import net.inetalliance.potion.info.PersistenceError;
import net.inetalliance.potion.info.Property;
import net.inetalliance.sql.DbVendor;
import net.inetalliance.sql.Namer;
import net.inetalliance.sql.SqlBuilder;
import net.inetalliance.sql.Where;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Autocomplete<T>
    extends Query<T>
    implements Comparator<T> {

  private final String[] terms;
  private final Integer limit;
  public Info<T> info;

  public Autocomplete(final Class<T> type, final String... terms) {
    this(type, null, terms);
  }

  public Autocomplete(final Class<T> type, final Integer limit, final String... terms) {
    super(type, String.format("%sA%d", type.getName(), limit), t -> {
      for (final Property<T, ?> property : Info.$(type).properties) {
        final Autocompletable annotation = property.field.getAnnotation(Autocompletable.class);
        if (annotation != null) {
          final Object value = property.apply(t);
          if (value != null) {
            final String string = value.toString().toLowerCase();
            for (final String term : terms) {
              if (string.contains(term)) {
                return true;
              }
            }
          }
        }
      }
      return false;

    }, (namer, table) -> {
      final Collection<Where> wheres = Info.$(type)
          .properties()
          .filter(Property::isAutocompletable)
          .map(Property::getColumns)
          .flatMap(Iterables::stream)
          .flatMap(column -> Stream.of(terms)
              .map(term -> column.getAutoCompleteWhere(namer, table,
                  column.name,
                  term)))
          .collect(Collectors.toList());

      if (wheres.isEmpty()) {
        throw new PersistenceError(
            "Trying to run autocomplete query on a %1$s, but %1$s doesn't have "
                + "any properties marked @%2$s",
            type.getSimpleName(), Autocompletable.class.getSimpleName());
      }
      return Where.or(wheres);
    });
    this.limit = limit;
    this.terms = Arrays.stream(terms).map(String::toLowerCase).toArray(String[]::new);
  }

  // --------------------- Interface Comparator ---------------------
  @Override
  public int compare(final T o1, final T o2) {
    return countMatches(o2) - countMatches(o1); // reversed to sort most matches to top
  }

  private int countMatches(final T object) {
    int matches = 0;
    for (final Property<T, ?> property : info.properties) {
      final Autocompletable annotation = property.field.getAnnotation(Autocompletable.class);
      if (annotation != null) {
        final Object value = property.apply(object);
        if (value != null) {
          final String string = value.toString().toLowerCase();
          for (final String term : terms) {
            if (string.contains(term)) {
              matches++;
            }
          }
        }
      }
    }
    return matches;
  }

  @Override
  public Iterable<Object> build(final SqlBuilder sql, final Namer namer, final DbVendor vendor,
      final String table) {
    if (limit != null) {
      sql.limit(limit);
    }
    return super.build(sql, namer, vendor, table);
  }

}
