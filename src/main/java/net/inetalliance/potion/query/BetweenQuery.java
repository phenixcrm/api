package net.inetalliance.potion.query;

import net.inetalliance.potion.info.Info;
import net.inetalliance.potion.info.Property;
import net.inetalliance.sql.DateTimeInterval;
import net.inetalliance.sql.Where;

import java.time.LocalDateTime;

public class BetweenQuery<T>
    extends Query<T> {

  public BetweenQuery(final Class<T> type, final String property, final DateTimeInterval interval) {
    this(type, Info.$(type).get(property), interval);

  }

  private BetweenQuery(final Class<T> type, final Property<T, LocalDateTime> property,
      final DateTimeInterval interval) {
    super(type, t -> {
      LocalDateTime o = property.apply(t);
      return o != null && interval.contains(o);
    }, (namer, table) -> Where
        .between(namer.name(type), property.getColumns().iterator().next().name, interval));
  }
}
