package net.inetalliance.potion.query;

import static java.lang.String.format;

import net.inetalliance.potion.info.Info;
import net.inetalliance.potion.info.Property;
import net.inetalliance.sql.Where;

public class LikeQuery<T>
    extends Query<T> {

  public LikeQuery(final Class<T> type, final String property, final String pattern) {
    this(type, Info.$(type).get(property), pattern, false);
  }

  private LikeQuery(final Class<T> type, final Property<T, String> property, final String pattern,
      final boolean caseSensitive) {
    super(type, format("%s.%s~", type.getName(), property), t -> {
      final String v = property.apply(t);
      return caseSensitive ? pattern.equals(v) : pattern.equalsIgnoreCase(v);
    }, (namer, table) -> Where.like(table, property.field.getName(), pattern, caseSensitive));

  }

  public LikeQuery(final Class<T> type, final String property, final String pattern,
      final boolean caseSensitive) {
    this(type, Info.$(type).get(property), pattern, caseSensitive);

  }
}
