package net.inetalliance.potion.jdbc;

import com.ameriglide.phenix.core.Log;
import lombok.Getter;
import net.inetalliance.potion.Hash;
import net.inetalliance.potion.Locator;
import net.inetalliance.potion.Version;
import net.inetalliance.potion.info.Info;
import net.inetalliance.potion.info.PersistenceError;
import net.inetalliance.potion.info.Property;
import net.inetalliance.sql.DbVendor;
import net.inetalliance.sql.Namer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static net.inetalliance.potion.Locator.types;

public class JdbcCursor {

    private static final Log log = new Log();
    private static final Pattern missingRelation = Pattern.compile(".*relation \"(.*)\" does not exist");
    public final DbVendor vendor;
    public final Namer namer;
    public String database;
    @Getter
    private final DataSource dataSource;

    public JdbcCursor(final DataSource dataSource, final DbVendor vendor, final Namer namer) {
        this.dataSource = dataSource;
        this.vendor = vendor;
        this.namer = namer;
    }

    @SuppressWarnings({"UnusedDeclaration", "SqlSourceToSinkFlow"})
    public void execute(final String sql, final Consumer<PreparedStatement> preExecute) throws SQLException {
        try (Connection cxn = getConnection()) {
            try (PreparedStatement statement = cxn.prepareStatement(sql)) {
                preExecute.accept(statement);
                statement.execute();
            }
        }
    }

    private Connection getConnection() {
        try {
            final Connection connection = dataSource.getConnection();
            connection.setAutoCommit(true);
            return connection;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void executeQuery(final String sql, final Consumer<PreparedStatement> preExecute,
                             final Consumer<ResultSet> consumer) throws SQLException {
        try (var cxn = getConnection()) {
            try (var statement = cxn.prepareStatement(sql)) {
                preExecute.accept(statement);
                try (var rs = statement.executeQuery()) {
                    consumer.accept(rs);
                }
            }
        }
    }

    public boolean execute(final String query) throws SQLException {
        try (Connection cxn = getConnection()) {
            try (PreparedStatement statement = cxn.prepareStatement(query)) {
                return statement.execute();
            }
        }
    }

    public <T> void insert(final String author, final T po) throws SQLException {
        final Info<T> info = Info.$(po);

        if (info.hasGeneratedProperties) {
            executeQuery(info.getInsert(vendor, namer, po), resultSet -> {
                try {
                    resultSet.next();
                    info.properties.stream().filter(Property::isGenerated).forEach(generated -> {
                        try {
                            generated.field.set(po, generated.read(resultSet, vendor, po));
                        } catch (final IllegalAccessException | SQLException e) {
                            throw new PersistenceError(e);
                        }
                    });
                } catch (final SQLException e) {
                    throw new PersistenceError(e);
                }
            });
        } else {
            execute(info.getInsert(vendor, namer, po));
        }
        if (info.versioned) {
            execute(info.getInsertVersion(vendor, namer, author, po));
        }
    }

    private void executeQuery(final SqlQuery query, final Consumer<ResultSet> proc) throws SQLException {
        executeQuery(query, (Predicate<ResultSet>) arg -> {
            proc.accept(arg);
            return true;
        });
    }

    public boolean execute(final SqlQuery query) throws SQLException {
        try (Connection cxn = getConnection()) {
            try (PreparedStatement statement = cxn.prepareStatement(query.sql)) {
                log.debug(() -> query.sql);
                int i = 1;
                for (final BiConsumer<PreparedStatement, Integer> parameter : query.parameters) {
                    parameter.accept(statement, i++);
                }
                return statement.execute();
            }
        }
    }

    private boolean executeQuery(final SqlQuery query, final Predicate<ResultSet> predicate) throws SQLException {
        try (Connection cxn = getConnection()) {
            try (PreparedStatement statement = cxn.prepareStatement(query.sql, ResultSet.TYPE_SCROLL_INSENSITIVE,
                    ResultSet.CONCUR_READ_ONLY)) {
                int i = 1;
                for (final BiConsumer<PreparedStatement, Integer> parameter : query.parameters) {
                    parameter.accept(statement, i++);
                }
                log.debug(() -> query.sql);
                QueryTracker.$(query.sql);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return predicate.test(resultSet);
                }
            }
        }
    }

    public <T> void update(final String author, final T old, final T updated) throws SQLException {
        final Info<T> info = Info.$(old);
        final SqlQuery update = info.getUpdate(vendor, namer, old, updated);
        if (update==null) {
            log.debug(() -> "no changes for %s".formatted(new Hash<>(old)));
        } else {
            execute(update);
            if (info.versioned) {
                for (final SqlQuery query : info.getUpdateVersion(vendor, namer, author, old, updated)) {
                    execute(query);
                }
            }
        }
    }

    public <T> boolean select(final T p) throws SQLException {
        final Info<T> info = Info.$(p);
        synchronized (p) {
            return executeQuery(info.getSelect(vendor, namer, p), (ResultSet r) -> {
                try {
                    if (r.next()) {
                        for (final Property<T, ?> property : info.properties) {
                            property.field.set(p, property.read(r, vendor, p));
                        }
                        Locator.setRead(p, true);
                        return true;
                    }
                    return false;
                } catch (final SQLException | IllegalAccessException e) {
                    throw new PersistenceError(e);
                }
            });
        }
    }

    public <T> void delete(final String author, final T p) throws SQLException {
        final Info<T> info = Info.$(p);
        execute(info.getDelete(vendor, namer, p));
        if (info.versioned) {
            execute(info.getDeleteVersion(vendor, namer, author, p));
        }
    }

    public <T> void createTable(final Class<T> type) {
        createTable(type, 0);
    }

    private <T> void createTable(final Class<T> type, final int depth) {
        if (depth > 10) {
            throw new PersistenceError("Excessive recursion when creating schema for %s", type.getSimpleName());
        }
        log.info(() -> "Creating tables for %s".formatted(type.getSimpleName()));
        final Info<T> poInfo = Info.$(type);
        final List<String> createStatements = poInfo.getCreate(vendor, namer);
        var i = new AtomicInteger(0);
        for (final String create : createStatements) {
            i.incrementAndGet();
            log.info(() -> "%d: %s".formatted(i.get(), create));
            try {
                execute(create);
            } catch (final SQLException e) {
                if (e.getMessage().contains("\"plpgsql\" does not exist")) {
                    try {
                        log.info(() -> "Adding PLPGSQL Extension to PostgreSQL");
                        execute("CREATE EXTENSION PLPGSQL;");
                        execute(create);
                    } catch (final SQLException se) {
                        throw new PersistenceError(se);
                    }
                } else {
                    final Matcher matcher = missingRelation.matcher(e.getMessage());
                    if (matcher.matches()) {
                        final String relation = matcher.group(1);
                        final Class<?> missingType = types.get(relation);
                        if (missingType==null) {
                            throw new PersistenceError("missing relation %s, but no corresponding registered type",
                                    relation);
                        } else {
                            createTable(missingType);
                            createTable(type, depth + 1);
                        }
                    } else if (!e.getMessage().contains("already exists")) {
                        throw new PersistenceError(e);
                    }
                }
            }
        }

    }

    public <O> O revert(final O o, final Version v) throws SQLException {
        final Info<O> info = Info.$(o);
        return executeQuery(info.getSelectVersion(vendor, namer, o, v), (Function<ResultSet, O>) r -> {
            final O copy = Locator.clone(o);
            try {
                synchronized (copy) {
                    while (r.next()) {
                        final O updated = info.type.getDeclaredConstructor().newInstance();
                        for (final Property<O, ?> property : info.properties) {
                            property.field.set(updated, property.read(r, vendor, updated));
                        }
                        if (r.next()) {
                            final O old = info.type.getDeclaredConstructor().newInstance();
                            for (final Property<O, ?> property : info.properties) {
                                property.field.set(old, property.read(r, vendor, old));
                            }
                            info.properties.stream().filter(Property.modified(updated, old)).forEach(p -> set(copy, p));
                        }
                    }
                }
            } catch (final Exception e) {
                throw new PersistenceError(e);
            }
            return copy;
        });
    }

    public <R> R executeQuery(final SqlQuery query, final Function<ResultSet, R> functor) throws SQLException {
        try (Connection cxn = getConnection()) {
            try (PreparedStatement statement = cxn.prepareStatement(query.sql, ResultSet.TYPE_SCROLL_INSENSITIVE,
                    ResultSet.CONCUR_READ_ONLY)) {
                int i = 1;
                for (final BiConsumer<PreparedStatement, Integer> parameter : query.parameters) {
                    parameter.accept(statement, i++);
                }
                log.debug(() -> query.sql);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return functor.apply(resultSet);
                }
            }
        } catch (final PersistenceError e) {
            if (e.getCause() instanceof SQLException) {
                log.error(() -> query.sql, e);
            }
            throw e;
        } catch (final SQLException e) {
            log.error(() -> query.sql, e);
            throw e;
        }
    }

    private <O, P> void set(O obj, Property<O, P> property) {
        try {
            property.field.set(obj, property.apply(obj));
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public <O> Version getVersion(final O o, final Integer versionId) throws SQLException {
        return executeQuery(Info.$(o).getVersion(vendor, namer, o, versionId), (Function<ResultSet, Version>) r -> {
            try {
                r.next();
                final Version version = new Version();
                for (final Property<Version, ?> property : Info.$(version).properties) {
                    property.field.set(version, property.read(r, vendor, version));
                }
                return version;
            } catch (final Exception e) {
                throw new PersistenceError(e);
            }
        });
    }

    public <O> List<Version> getVersions(final O o) throws SQLException {
        final List<Version> versions = new ArrayList<>(0);
        final Info<O> info = Info.$(o);
        executeQuery(info.getVersions(vendor, namer, o), r -> {
            try {
                while (r.next()) {
                    final O updated = info.type.getDeclaredConstructor().newInstance();
                    for (final Property<O, ?> property : info.properties) {
                        property.field.set(updated, property.read(r, vendor, updated));
                    }
                    final Version version = new Version();
                    versions.add(version);
                    for (final Property<Version, ?> property : Info.$(version).properties) {
                        property.field.set(version, property.read(r, vendor, version));
                    }
                    if (r.next()) {
                        final O old = info.type.getDeclaredConstructor().newInstance();
                        for (final Property<O, ?> property : info.properties) {
                            property.field.set(old, property.read(r, vendor, old));
                        }
                        version.modifiedFields = info.properties
                                .stream()
                                .filter(Property.modified(updated, old))
                                .map(Property::getName)
                                .collect(Collectors.joining(", "));
                    }
                }
            } catch (final Exception e) {
                throw new PersistenceError(e);
            }
        });
        return versions;
    }
}
