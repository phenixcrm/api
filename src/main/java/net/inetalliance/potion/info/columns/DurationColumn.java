package net.inetalliance.potion.info.columns;

import java.time.Duration;

public class DurationColumn extends ProxyColumn<Long, Duration> {

    public DurationColumn(final String name, final boolean required, final boolean unique) {
        super(new LongColumn(name, required, unique));
    }

    @Override
    protected Duration parse(Long value) {
        return value == null ? null : Duration.ofMillis(value);
    }

    @Override
    protected Long format(Duration value) {
        return value == null ? null : value.toMillis();
    }

    @Override
    public Duration[] newArray(int size) {
        return new Duration[size];
    }
}
