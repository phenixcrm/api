package net.inetalliance.potion;

import com.ameriglide.phenix.core.Log;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.val;
import net.inetalliance.potion.annotations.Persistent;
import net.inetalliance.potion.cache.NullCache;
import net.inetalliance.potion.cache.ObjectCache;
import net.inetalliance.potion.info.Info;
import net.inetalliance.potion.info.PersistenceError;
import net.inetalliance.potion.info.Property;
import net.inetalliance.potion.info.UniqueKeyError;
import net.inetalliance.potion.info.columns.AggregateColumn;
import net.inetalliance.potion.jdbc.JdbcCursor;
import net.inetalliance.potion.jdbc.Parameters;
import net.inetalliance.potion.jdbc.SqlQuery;
import net.inetalliance.potion.obj.Positioned;
import net.inetalliance.potion.query.Query;
import net.inetalliance.potion.query.SortedQuery;
import net.inetalliance.sql.*;
import net.inetalliance.types.Pointer;
import net.inetalliance.types.struct.caches.LruCache;
import net.inetalliance.types.struct.sets.AddSortedSet;
import net.inetalliance.util.ProgressMeter;
import net.inetalliance.validation.ValidationErrors;
import net.inetalliance.validation.Validator;
import net.inetalliance.validation.processors.JavascriptProcessor;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

import static com.ameriglide.phenix.core.Consumers.index;
import static com.ameriglide.phenix.core.Strings.isEmpty;
import static java.lang.String.format;
import static java.util.Collections.singleton;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static net.inetalliance.sql.Aggregate.GROUP_BY;

public class Locator {

    public static final Collection<LocatorListener> listeners;
    public static final Namer namer = new SimpleNamer() {
        @Override
        public String name(final Class<?> type) {
            var persistent = type.getAnnotation(Persistent.class);
            if (persistent != null) {
                var tableName = persistent.tableName();
                return isEmpty(tableName) ? super.name(type) : tableName;
            }
            return super.name(type);
        }
    };
    private static final Log log = new Log();
    private static final Map<Class<?>, ObjectCache<?>> objectCaches;
    private static final boolean queryCachingDisabled = System.getProperty("Locator.queryCachingDisabled") != null;
    private static final Map<Class<?>, Collection<QueryCacheKey>> queryTypeDependencies;
    private static final ConcurrentHashMap<Class<?>, Field> readField = new ConcurrentHashMap<>(32);
    public static Map<String, Class<?>> types;
    public static JdbcCursor jdbc;
    private static Map<QueryCacheKey, Object> queryCache;
    private static final Set<Class<?>> cacheableTypes = Collections.synchronizedSet(new HashSet<>(32));

    static {
        if (queryCachingDisabled) {
            log.warn(() -> "LOCATOR: Query caching disabled.");
        } else {
            log.info(() -> "LOCATOR: Query caching enabled.");
        }
    }

    public static void enableCaching(final Class<?>... types) {
        Collections.addAll(cacheableTypes, types);
        log.info(() -> "Simple object caching is on for [%s] types".formatted(
                Arrays.stream(types).map(Class::getSimpleName).collect(joining(", "))));
    }

    static {
        objectCaches = Collections.synchronizedMap(new HashMap<>());
        JavascriptProcessor.getFieldValue = JavascriptProcessor.ext;
        types = new TreeMap<>((a, b) -> {
            if (a == null) {
                return b == null ? 0 : -1;
            }
            if (b == null) {
                return 1;
            }
            return a.toLowerCase().compareTo(b.toLowerCase());
        });
        listeners = new ArrayList<>(0);
    }

    public record TypedEnumSet<E extends Enum<E>>(Class<E> type, Set<E> set) {
    }


    @SuppressWarnings("unchecked")
    public static <E extends Enum<E>> TypedEnumSet<E> makeEnumSet(String name) {
        val type = (Class<E>) types.get(name);
        return new TypedEnumSet<>(type, type == null ? null : EnumSet.allOf(type));
    }

    static {
        queryCache = new LruCache<>(256);
        queryTypeDependencies = new HashMap<>(64);

        addListener(new LocatorListener() {
            @Override
            public void create(final Object object) {
                clearQueryCache(object.getClass());
            }

            @Override
            public void update(final Object old, final Object updated) {
                clearQueryCache(updated.getClass());
            }

            @Override
            public void delete(final Object object) {
                clearQueryCache(object.getClass());
            }
        });
    }

    private Locator() {
        // only static methods. thou shalt not construct!

    }

    public static void addListener(final LocatorListener listener) {
        listeners.add(listener);
    }

    /**
     * gets a fully read object from the cache or from jdbc if necessary
     *
     * @param t an object with its key set
     * @return the canonical object, or null, if no object is found with the keys in t
     */
    public static <O> O $(final O t) {
        if (t == null) {
            return null;
        }
        var canonical = getCanonical(t);
        if (isRead(canonical)) {
            return canonical;
        } else {
            return read(canonical, false) ? canonical : null;
        }
    }

    /**
     * gets the canonical copy of an object, either from the cache or by creating it
     *
     * @param t an object with its key set
     * @return the canonical object, or null, if no object is found with the keys in t
     */
    public static <O> O getCanonical(final O t) {
        if (t == null) {
            return null;
        }
        var hash = new Hash<>(t);
        var object = getCached(hash);
        if (object == null) {
            log.debug(() -> "new reference %s".formatted(hash));
            return t;
        } else {
            setRead(object, true);
            return object;
        }
    }

    private static <O> void cache(O o) {
        if (o != null) {
            var hash = new Hash<>(o);
            var cache = getCache(hash.type);
            if (cache != null) {
                cache.add(hash, o);
            }
        }
    }

    @SuppressWarnings("WeakerAccess")
    public static boolean isRead(final Object object) {
        if (object == null) {
            return false;
        }
        var field = getReadField(object.getClass());
        try {
            var read = (Boolean) field.get(object);
            return read != null && read;
        } catch (final IllegalAccessException e) {
            throw new PersistenceError(e);
        }
    }

    public static <O> boolean read(final O object, final boolean checkCache) {
        var info = Info.$(object);
        if (!info.locatable) {
            return false;
        }
        log.debug(() -> "R %s".formatted(new Hash<>(object)));
        try {
            if (!isRead(object)) {
                if (checkCache) {
                    var cached = getCached(new Hash<>(object));
                    if (cached != null) {
                        copy(cached, object);
                        return true;
                    }
                }
                try {
                    return jdbc.select(object);
                } finally {
                    cache(object);
                }
            }
            return true;
        } catch (final SQLException e) {
            if (!e
                    .getMessage()
                    .contains(format("relation \"%s\" does not exist", object.getClass().getSimpleName().toLowerCase()))) {
                throw new PersistenceError(e);
            }
        }
        return false;
    }

    public static <O> void copy(final O src, final O dest) {
        var info = Info.$(src);
        try {
            for (var property : info.properties) {
                log.trace(() -> "Cloning %s.%s...".formatted(info.type.getSimpleName(), property.field.getName()));
                property.clone(src, dest);
            }
        } catch (final InstantiationException |
                       InvocationTargetException |
                       NoSuchMethodException |
                       IllegalAccessException e) {
            throw new PersistenceError(e);
        }
    }

    public static void register(final Class<?>... types) {
        for (var type : types) {
            if (type.getAnnotation(Persistent.class) != null) {
                Info.$(type);
            }
            Locator.types.put(type.getSimpleName(), type);
            log.debug(() -> "REG %s".formatted(type.getSimpleName()));
        }
    }

    public static void detach() {
        shutdown("Object Caches", objectCaches::clear);
        shutdown("Query Cache", () -> {
            queryCache.clear();
            queryTypeDependencies.clear();
        });

    }

    private static void shutdown(final String label, final Runnable task) {
        log.info(() -> "Shutdown %s".formatted(label));
        try {
            task.run();
        } catch (Throwable t) {
            log.error(t);
        }

    }

    public static void attach(final DataSource dataSource) {
        jdbc = new JdbcCursor(dataSource, DbVendor.POSTGRES, Namer.simple);
    }

    public static void attach(final Db db) {
        jdbc = new JdbcCursor(startPool(db), db.vendor, Namer.simple);
    }

    private static DataSource startPool(final Db db) {
        var h = new HikariConfig();
        h.setUsername(db.user);
        h.setPassword(db.password);
        h.setJdbcUrl(db.toString());
        h.setMaximumPoolSize(db.poolSize);
        return new HikariDataSource(h);

    }

    public static <O> void updateCache(final Hash<O> hash, final O old, final O updated) {
        getCache(hash.type).update(hash, updated);
        for (var listener : listeners) {
            listener.update(old, updated);
        }
    }

    public static <O> void create(final String author, final O object) {
        create(author, object, true);
    }

    private static <O> void create(final String author, final O object, final boolean create) {
        log.debug(() -> "C %s".formatted(new Hash<>(object)));
        var info = Info.$(object);
        try {
            if (info.creationListener) {
                info.asCreationListener(object).onCreate();
            }
            jdbc.insert(author, object);
            Locator.setRead(object, true);
            for (var listener : listeners) {
                listener.create(object);
            }
            if (info.creationListener) {
                info.asCreationListener(object).afterCreation();
            }
        } catch (final PersistenceError e) {
            var message = e.getCause().getMessage();
            if (create && message.contains("Unknown type")) {
                jdbc.createTable(object.getClass());
                create(author, object, false);
            } else {
                log.error(e);
                throw e;
            }
        } catch (final SQLException e) {
            var message = e.getMessage();
            if (create && message.startsWith(
                    format("ERROR: relation \"%s\" does not exist", object.getClass().getSimpleName().toLowerCase()))) {
                jdbc.createTable(object.getClass());
                create(author, object, false);
            } else if (message.contains("duplicate key value violates unique constraint")) {
                throw new UniqueKeyError(e);
            } else {
                throw new PersistenceError(e);
            }
        }
    }

    private static <O> void update(final String author, final O old, final O updated) {
        synchronized (old) {
            var oldHash = new Hash<>(old);
            uncache(oldHash);
            try {
                var newHash = new Hash<>(updated);
                log.debug(() -> "U %s->%s".formatted(oldHash, newHash));
                var info = Info.$(updated);
                if (info.updateListener) {
                    info.asUpdateListener(updated).onUpdate(old);
                }
                jdbc.update(author, old, updated);
                for (var property : info.properties) {
                    try {
                        property.field.set(old, property.apply(updated));
                    } catch (final IllegalAccessException e) {
                        throw new PersistenceError(e);
                    }
                }
                updateCache(newHash, old, updated);
                if (info.updateListener) {
                    info.asUpdateListener(updated).afterUpdate();
                }
            } catch (final SQLException e) {
                throw new PersistenceError(e);
            }
        }
    }

    public static <O> ValidationErrors update(final O t, final String user, final Function<O, ValidationErrors> update) {
        synchronized (t) {
            var copy = clone(t);
            var errors = update.apply(copy);
            if (errors.isEmpty()) {
                try {
                    update(user, t, copy);
                } catch (final PersistenceError e) {
                    if (e.getMessage() != null && e.getMessage().contains("duplicate key value violates unique constraint")) {
                        var info = Info.$(t);
                        var name = info.properties.stream().filter(Property::isKey).map(Property::getName).findFirst().orElse(null);
                        errors.put(name, singleton(Validator.messages.get(Locale.US, "validation.uniqueKey",
                                Validator.messages.get(Locale.US, info.type.getSimpleName()), name)));
                    } else {
                        throw e;
                    }
                }
            }
            return errors;
        }
    }

    public static <O> O clone(final O src) {
        var info = Info.$(src);
        read(src);
        try {
            var dest = info.type.getDeclaredConstructor().newInstance();
            clone(src, dest);
            setRead(dest, true);
            return dest;
        } catch (final InstantiationException |
                       IllegalAccessException |
                       NoSuchMethodException |
                       InvocationTargetException e) {
            throw new PersistenceError(e);
        }
    }

    public static <O> boolean read(final O object) {
        return read(object, true);
    }

    public static <O> void update(final O t, final String user, final Consumer<O> proc) {
        if (t == null) {
            return;
        }
        synchronized (t) {
            var copy = clone(t);
            if (!isRead(copy)) {
                read(copy);
            }
            proc.accept(copy);
            update(user, t, copy);
        }
    }

    public static <O> void clone(final O src, final O dest) {
        read(src);
        copy(src, dest);
    }

    public static <O> Consumer<O> delete(final String author) {
        return arg -> delete(author, arg);
    }

    public static <O> void delete(final String author, final O object) {
        var hash = new Hash<>(object);
        log.debug(() -> "D %s".formatted(hash));
        uncache(hash);
        try {
            jdbc.delete(author, object);
            for (var listener : listeners) {
                listener.delete(object);
            }
        } catch (final SQLException e) {
            throw new PersistenceError(e);
        }
    }

    private static <O> void uncache(final Hash<O> hash) {
        getCache(hash.type).remove(hash);
    }

    @SuppressWarnings("unchecked")
    private synchronized static <T> ObjectCache<T> getCache(final Class<T> type) {
        if (cacheableTypes.contains(type)) {
            return (ObjectCache<T>) objectCaches.computeIfAbsent(type,
                    _ -> ObjectCache.$("Locator", type.getSimpleName(), type));
        }
        return ObjectCache.nullCache();
    }

    public static <T> Set<T> $A(final Class<T> type) {
        return $$(Query.all(type));
    }

    public static <T> Set<T> $$(final Query<T> query) {
        return cacheQuery(query, _ -> collect(query, new AddSortedSet<>()));
    }

    private static <T, C extends Collection<T>> C collect(final Query<T> query, final C results) {
        forEach(query, results::add);
        return results;
    }

    public static <T, R> R $$(final Query<T> query, final Aggregate function, final Class<R> type, final String name) {
        return cacheQuery(query, _ -> {
            try {
                var aggregate = AggregateColumn.$(function, name, type);
                return jdbc.executeQuery(query.asSql(jdbc.vendor, jdbc.namer, aggregate.field), (ResultSet arg1) -> {
                    try {
                        if (arg1.next()) {
                            return aggregate.read(aggregate.field.aggregate.name(), arg1, jdbc.vendor);
                        } else {
                            return null;
                        }
                    } catch (final SQLException e) {
                        throw new PersistenceError(e);
                    }
                });
            } catch (final SQLException e) {
                throw new PersistenceError(e);
            }
        }, function.name(), type.getName(), name);
    }

    @SuppressWarnings("unchecked")
    private static <R, T> R cacheQuery(final Query<T> q, final Function<Query<T>, R> proc,
                                       final String... additionalKeys) {
        if (queryCachingDisabled || !q.isCacheable()) {
            return proc.apply(q);
        }
        var parameters = new Parameters();
        var i = 1;
        for (var parameter : q.asSql(jdbc.vendor, Namer.simple).parameters) {
            parameter.accept(parameters, i++);
        }
        var key = new QueryCacheKey(q, parameters, additionalKeys);
        if (queryCache.containsKey(key)) {
            log.trace(() -> "Servicing %s from query cache %s".formatted(key, queryCache));
            return (R) queryCache.get(key);
        } else {
            var r = proc.apply(q);
            queryCache.put(key, r);
            for (final Class<?> type : q.getTypeDependencies()) {
                queryTypeDependencies.computeIfAbsent(type, _ -> new ArrayList<>(8)).add(key);
            }
            log.trace(() -> "Adding results for %s to the query cache %s".formatted(key, queryCache));
            return r;
        }
    }

    public static <T, K, V> Map<K, V> $$(final Query<T> query, final Aggregate function, final Class<K> keyType,
                                         final String key, final Class<V> valueType, final String value) {
        try {
            var keyColumn = AggregateColumn.$(GROUP_BY, key, keyType);
            var valueColumn = AggregateColumn.$(function, value, valueType);
            return jdbc.executeQuery(query.asSql(jdbc.vendor, jdbc.namer, keyColumn.field, valueColumn.field),
                    (ResultSet arg) -> {
                        final Map<K, V> map = new HashMap<>();
                        try {
                            while (arg.next()) {
                                var k = keyColumn.read(key, arg, jdbc.vendor);
                                if (k != null) {
                                    map.put(k, valueColumn.read(valueColumn.field.aggregate.name(), arg, jdbc.vendor));
                                }
                            }
                        } catch (final SQLException e) {
                            throw new PersistenceError(e);
                        }
                        return map;
                    });
        } catch (final SQLException e) {
            throw new PersistenceError(e);
        }
    }

    public static <T, G, K, V> Map<G, Map<K, V>> $$(final Query<T> query, final Aggregate function,
                                                    final Class<G> groupType, final String group, final Class<K> keyType,
                                                    final String key, final Class<V> valueType, final String value) {
        try {
            var groupColumn = AggregateColumn.$(GROUP_BY, group, groupType);
            var keyColumn = AggregateColumn.$(GROUP_BY, key, keyType);
            var valueColumn = AggregateColumn.$(function, value, valueType);
            return jdbc.executeQuery(
                    query.asSql(jdbc.vendor, jdbc.namer, groupColumn.field, keyColumn.field, valueColumn.field),
                    (ResultSet arg) -> {
                        final Map<G, Map<K, V>> map = new HashMap<>();
                        try {
                            while (arg.next()) {
                                var g = groupColumn.read(group, arg, jdbc.vendor);
                                if (g != null) {
                                    var kvMap = map.computeIfAbsent(g, _ -> new HashMap<>());
                                    var k = keyColumn.read(key, arg, jdbc.vendor);
                                    if (k != null) {
                                        kvMap.put(k, valueColumn.read(valueColumn.field.aggregate.name(), arg, jdbc.vendor));
                                    }
                                }
                            }
                        } catch (final SQLException e) {
                            throw new PersistenceError(e);
                        }
                        return map;
                    });
        } catch (final SQLException e) {
            throw new PersistenceError(e);
        }
    }

    public static <T> Function<ResultSet, Integer> read(Class<T> type, Consumer<T> consumer) {
        return read(type, consumer, false);
    }

    public static <T> Function<ResultSet, Integer> read(Class<T> type, Consumer<T> consumer, boolean isKeysOnly) {
        var info = Info.$(type);
        final List<Object> keyValues = new ArrayList<>(2);
        return (ResultSet r) -> {
            try {
                var count = 0;
                while (r.next()) {
                    keyValues.clear();

                    info.properties.stream().filter(Property::isKey).forEach((Property<?, ?> key) -> {
                        try {
                            keyValues.add(key.read(r, jdbc.vendor, null));
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    });

                    final Object o = getCached(new Hash<>(type, keyValues));
                    if (o != null) {
                        consumer.accept(type.cast(o));
                    } else {
                        var t = type.getDeclaredConstructor().newInstance();
                        info.keys().forEach(index((Property<T, ?> key, Integer i) -> {
                            try {
                                key.field.set(t, keyValues.get(i));
                            } catch (IllegalAccessException e) {
                                throw new RuntimeException(e);
                            }
                        }));

                        if (isKeysOnly) {
                            consumer.accept($(t));
                        } else {
                            setFields(r, info, t);
                            Locator.setRead(t, true);
                            cache(t);
                            consumer.accept(t);
                        }
                    }
                    count++;
                }
                return count;
            } catch (final SQLException |
                           InstantiationException |
                           NoSuchMethodException |
                           InvocationTargetException |
                           IllegalAccessException e) {
                log.error(e);
                throw new PersistenceError(e);
            }
        };
    }

    private static <T> void setFields(final ResultSet r, final Info<T> info, final T t) {
        info.properties.stream().filter(k -> !k.isKey()).forEach((Property<T, ?> property) -> {
            try {
                property.field.set(t, property.read(r, jdbc.vendor, t));
            } catch (IllegalAccessException | SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }


    public static <T> int forEach(final Query<T> query, final Consumer<T> consumer) {
        try {
            return jdbc.executeQuery(query.asSql(jdbc.vendor, jdbc.namer), read(query.type, consumer, query.isKeysOnly()));
        } catch (final SQLException e) {
            var message = e.getMessage();
            if (message.contains(format("relation \"%s\" does not exist", query.type.getSimpleName().toLowerCase()))) {
                return 0;
            } else {
                throw new PersistenceError(e);
            }
        }
    }

    public static <T> void forEachWithProgress(final Query<T> query, final BiConsumer<T, ProgressMeter> consumer) {
        Locator.forEach(query, ProgressMeter.consume(Locator.count(query), consumer));
    }

    public static <T, F> F forEach(final Query<T> query, final BiFunction<T, F, F> f2, final F startingValue) {
        var p = new Pointer<>(startingValue);

        forEach(query, t -> p.obj = f2.apply(t, p.obj));
        return p.obj;
    }

    @SuppressWarnings("SynchronizeOnNonFinalField")
    private static void clearQueryCache(final Class<?> type) {
        synchronized (queryCache) {
            var keys = queryTypeDependencies.remove(type);
            if (keys != null) {
                for (var key : keys) {
                    queryCache.remove(key);
                }
            }
        }
    }

    public static <T> T $1(final Class<T> type) {
        return $1(Query.all(type).limit(1));
    }

    @SafeVarargs
    public static <T> T $1(final Query<T>... queries) {
        return queries.length == 0 ? null : $1(Query.and(queries[0].type, Arrays.asList(queries)));
    }

    private static <T> void checkRelation(final Query<T> query, final SQLException e) {
        var message = e.getMessage();
        if (!message.contains(format("relation \"%s\" does not exist", query.type.getSimpleName().toLowerCase()))) {
            throw new PersistenceError(e);
        }
    }

    public static <T> T $1(final Query<T> query) {
        return cacheQuery(query, _ -> {
            try {
                var info = Info.$(query.type);
                final List<Object> keyValues = new ArrayList<>(2);
                return jdbc.executeQuery(query.limit(1).asSql(jdbc.vendor, jdbc.namer), (ResultSet r) -> {
                    try {
                        if (r.next()) {
                            keyValues.clear();
                            info.keys().forEach(key -> {
                                try {
                                    keyValues.add(key.read(r, jdbc.vendor, null));
                                } catch (SQLException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                            final Object o = getCached(new Hash<>(query.type, keyValues));
                            if (o != null) {
                                return query.type.cast(o);
                            } else {
                                var t = query.type.getDeclaredConstructor().newInstance();
                                info.properties().filter(not(Property::isKey)).forEach(property -> {
                                    try {
                                        property.field.set(t, property.read(r, jdbc.vendor, t));
                                    } catch (IllegalAccessException | SQLException e) {
                                        throw new RuntimeException(e);
                                    }
                                });
                                info.keys().forEach(index((Property<?, ?> key, Integer i) -> {
                                    try {
                                        key.field.set(t, keyValues.get(i));
                                    } catch (IllegalAccessException e) {
                                        throw new RuntimeException(e);
                                    }
                                }));
                                Locator.setRead(t, true);
                                cache(t);
                                return t;
                            }
                        } else {
                            return null;
                        }
                    } catch (final SQLException e) {
                        checkRelation(query, e);
                        return null;

                    } catch (final InstantiationException |
                                   IllegalAccessException |
                                   NoSuchMethodException |
                                   InvocationTargetException e) {
                        throw new PersistenceError(e);
                    }
                });
            } catch (final SQLException e) {
                checkRelation(query, e);
                return null;
            }
        }, "1");
    }

    public static <T> SortedSet<T> $$(final SortedQuery<T> query) {
        return cacheQuery(query, _ -> collect(query, new AddSortedSet<>()));
    }

    @SafeVarargs
    public static <T> Set<T> $$(final Query<T>... queries) {
        return queries.length == 0 ? Set.of() : $$(Query.and(queries[0].type, Arrays.asList(queries)));
    }

    public static <T> T find(final Query<T> query, final Predicate<T> predicate) {
        try {
            var info = Info.$(query.type);
            final List<Object> keyValues = new ArrayList<>(1);
            return jdbc.executeQuery(query.asSql(jdbc.vendor, jdbc.namer), (ResultSet r) -> {
                try {
                    while (r.next()) {
                        keyValues.clear();
                        info.keys().forEach((Property<T, ?> key) -> {
                            try {
                                keyValues.add(key.read(r, jdbc.vendor, null));
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        });

                        final Object o = getCached(new Hash<>(query.type, keyValues));
                        final T t;
                        if (o != null) {
                            t = query.type.cast(o);
                        } else {
                            t = query.type.getDeclaredConstructor().newInstance();
                            setFields(r, info, t);
                            var i = new AtomicInteger();
                            info.properties.stream().filter(Property::isKey).forEach(key -> {
                                try {
                                    key.field.set(t, keyValues.get(i.getAndIncrement()));
                                } catch (IllegalAccessException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                            Locator.setRead(t, true);
                            cache(t);
                        }
                        if (predicate.test(t)) {
                            return t;
                        }
                    }
                    return null;
                } catch (final SQLException |
                               InstantiationException |
                               IllegalAccessException |
                               NoSuchMethodException |
                               InvocationTargetException e) {
                    log.error(e);
                    throw new PersistenceError(e);
                }
            });
        } catch (final SQLException e) {
            var message = e.getMessage();
            if (message.contains(format("relation \"%s\" does not exist", query.type.getSimpleName().toLowerCase()))) {
                return null;
            } else {
                throw new PersistenceError(e);
            }
        }
    }

    @SuppressWarnings("unused")
    public static <T> void manyToMany(final Query<T> query, final BiConsumer<T, Integer> proc, final boolean sorted) {
        try {
            var info = Info.$(query.type);
            final List<Object> keyValues = new ArrayList<>(2);
            jdbc.executeQuery(query.asSql(jdbc.vendor, jdbc.namer), (ResultSet r) -> {
                try {
                    var count = 0;
                    while (r.next()) {
                        keyValues.clear();
                        info.keys().map(p -> p.rename("to", true, false, true)).forEach(key -> {
                            try {
                                keyValues.add(key.read(r, jdbc.vendor, null));
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        });
                        var position = sorted ? r.getInt(Positioned.property) : null;
                        var o = getCached(new Hash<>(query.type, keyValues));
                        if (o != null) {
                            proc.accept(o, position);
                        } else {
                            var t = query.type.getDeclaredConstructor().newInstance();
                            var i = new AtomicInteger();
                            info.keys().forEach(key -> {
                                try {
                                    key.field.set(t, keyValues.get(i.getAndIncrement()));
                                } catch (IllegalAccessException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                            proc.accept(t, position);
                        }
                        count++;
                    }
                    return count;
                } catch (final SQLException |
                               InstantiationException |
                               NoSuchMethodException |
                               IllegalAccessException |
                               InvocationTargetException e) {
                    log.error(e);
                    throw new PersistenceError(e);
                }
            });
        } catch (final SQLException e) {
            throw new PersistenceError(e);
        }
    }

    private static <O> O getCached(final Hash<O> hash) {
        var obj = getCache(hash.type).get(hash);
        if (obj != null) {
            Locator.setRead(obj, true);
        }
        return hash.type.cast(obj);
    }

    public static void setRead(final Object o, final boolean loaded) {
        try {
            getReadField(o.getClass()).set(o, loaded ? Boolean.TRUE : null);
        } catch (final PersistenceError e) {
            if (e.getCause() instanceof NoSuchMethodException) {
                if (o.getClass().getAnnotation(Persistent.class) == null) {
                    log.error(() -> "trying to use type %s in locator, but that type is not marked as Persistent".formatted(
                            o.getClass().getSimpleName()));
                } else {
                    log.warn(() -> "agent not enabled", e);
                }
            } else {
                throw e;
            }
        } catch (final IllegalAccessException e) {
            throw new PersistenceError(e);
        }
    }

    private static Field getReadField(final Class<?> type) {
        return readField.computeIfAbsent(type, t -> {
            try {
                var $$read = t.getDeclaredField("$$read");
                $$read.setAccessible(true);
                return $$read;
            } catch (NoSuchFieldException e) {
                log.error(
                        () -> "could not find $$read on %s: %s".formatted(type.getSimpleName(), type.getClassLoader().toString()));
                throw new PersistenceError(e);
            }
        });
    }

    public static <T> int count(final Query<T> query) {
        return count(query, "*");
    }

    public static <T> int countDistinct(final Query<T> query, final String field) {
        return count(query, "DISTINCT " + field);
    }

    private static <T> int count(final Query<T> query, final String field) {
        return cacheQuery(query, _ -> {
            try {
                if (query.isComplex()) {
                    return jdbc.executeQuery(query.asSql(jdbc.vendor, jdbc.namer), (ResultSet rs) -> {
                        try {
                            rs.last();
                            return rs.getRow();
                        } catch (final SQLException e) {
                            throw new PersistenceError(e);
                        }
                    });
                } else {
                    return jdbc.executeQuery(query.asSql(jdbc.vendor, jdbc.namer, new AggregateField(Aggregate.COUNT, field)),
                            (ResultSet countRs) -> {
                                try {
                                    countRs.next();
                                    return countRs.getInt(1);
                                } catch (final SQLException e) {
                                    throw new PersistenceError(e);
                                }
                            });
                }
            } catch (final SQLException e) {
                var message = e.getMessage();
                if (message.contains(format("relation \"%s\" does not exist", query.type.getSimpleName().toLowerCase()))) {
                    return 0;
                }
                throw new PersistenceError(e);
            }
        }, Aggregate.COUNT.name(), field);
    }

    public static <O> List<Version> getVersions(final O o) {
        try {
            return jdbc.getVersions(o);
        } catch (final SQLException e) {
            throw new PersistenceError(e);
        }
    }

    @SuppressWarnings("unused")
    public static <O> Version getVersion(final O o, final Integer version) {
        try {
            return jdbc.getVersion(o, version);
        } catch (final SQLException e) {
            throw new PersistenceError(e);
        }
    }

    @SuppressWarnings("unused")
    public static <O> Where getVersionCreationWhere(final Class<O> type, final Namer namer, final DateTimeInterval period) {
        return Info.$(type).getVersionCreationWhere(jdbc.vendor, namer, period);
    }

    static <O> O getReference(final Class<O> type, final String reference) {
        return type.cast(getCache(type).get(reference));
    }

    static <O> void updateReference(final Class<O> type, final String reference, final O obj) {
        getCache(type).update(reference, obj);
    }

    @SuppressWarnings("UnusedDeclaration")
    public static <T> void createTable(final Class<T> type) {
        jdbc.createTable(type);
    }

    public static String getDbName() {
        return jdbc.database;
    }

    @SuppressWarnings("unused")
    public static void disableQueryCaching() {
        queryCache = new NullCache<>();
        log.warn(() -> "Query caching is disabled");
    }

    @SuppressWarnings("unused")
    public static <R> R execute(final String query, final Function<ResultSet, R> function) {
        try {
            return jdbc.executeQuery(new SqlQuery(query), function);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


}
