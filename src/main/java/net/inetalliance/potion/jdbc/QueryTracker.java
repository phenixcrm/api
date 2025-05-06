package net.inetalliance.potion.jdbc;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;

public class QueryTracker {

    private static final ThreadLocal<QueryTracker> instance = new ThreadLocal<QueryTracker>();
    private final Collection<String> queries;
    private final long start;

    private QueryTracker() {
        queries = new ArrayList<String>(32);
        start = System.currentTimeMillis();
    }

    public static void start() {
        instance.set(new QueryTracker());
    }

    public static Stats end() {
        final QueryTracker tracker = instance.get();
        if (tracker == null) {
            throw new RuntimeException("You must call QueryTracker.start() first.");
        }
        instance.remove();
        return new Stats(tracker.queries.size(),
                Duration.ofMillis(System.currentTimeMillis() - tracker.start));
    }

    public static void $(final String query) {
        final QueryTracker tracker = instance.get();
        if (tracker != null) {
            tracker.queries.add(query);
        }
    }

    public static final class Stats {

        public final int count;
        public final Duration duration;

        public Stats(final int count, final Duration duration) {
            this.count = count;
            this.duration = duration;
        }
    }
}
