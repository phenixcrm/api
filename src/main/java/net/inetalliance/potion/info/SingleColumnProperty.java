package net.inetalliance.potion.info;

import com.ameriglide.phenix.core.Classes;
import com.ameriglide.phenix.core.Log;
import lombok.val;
import net.inetalliance.potion.annotations.Indexed;
import net.inetalliance.potion.annotations.Parsed;
import net.inetalliance.potion.annotations.PrimaryKey;
import net.inetalliance.potion.annotations.Searchable;
import net.inetalliance.potion.info.columns.*;
import net.inetalliance.sql.DbVendor;
import net.inetalliance.sql.Namer;
import net.inetalliance.sql.Where;
import net.inetalliance.types.Currency;
import net.inetalliance.types.annotations.*;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;
import net.inetalliance.types.localized.LocalizedString;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.Inet4Address;
import java.net.URI;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static net.inetalliance.potion.annotations.Indexed.Method.BTREE;

public class SingleColumnProperty<O, P>
        extends Property<O, P> {

    private static final Map<Class<?>, Generator<?>> generators;
    private static final Log log = new Log();

    static {
        generators = new HashMap<>();
        generators.put(Integer.class,
                (Generator<Integer>) (name, _, required, unique) ->
                        new IntegerColumn(name, required, unique));
        generators.put(Long.class,
                (Generator<Long>) (name, _, required, unique) ->
                        new LongColumn(name, required, unique));
        generators.put(boolean.class,
                (Generator<Boolean>) (name, _, _, unique) ->
                        new BooleanColumn(name, true, unique, false));
        generators.put(Boolean.class,
                (Generator<Boolean>) (name, _, required, unique) ->
                        new BooleanColumn(name, required, unique, null));
        generators.put(Currency.class,
                (Generator<Currency>) (name, _, required, unique) ->
                        new CurrencyColumn(name, required, unique));
        generators.put(Float.class,
                (Generator<Float>) (name, _, required, unique) ->
                        new FloatColumn(name, required, unique));
        generators.put(Double.class,
                (Generator<Double>) (name, field, required, unique) -> {
                    val percent = field == null ? null : field.getAnnotation(Percentage.class);
                    val format = field == null ? null : field.getAnnotation(Format.class);
                    return new DoubleColumn(name, required, unique,
                            percent == null ? format == null ? null : format.value() : "0.0%");
                });
        generators.put(Locale.class,
                (Generator<Locale>) (name, field, required, unique) ->
                        field == null || field.getAnnotation(Parsed.class) == null
                                ? new LocaleColumn(name, required, unique)
                                : new ParsingLocaleColumn(name, required, unique));
        generators.put(String.class,
                (Generator<String>) (name, field, required, unique) -> {
                    val maxLength = field == null ? null : field.getAnnotation(MaxLength.class);
                    val xOut = field == null ? null : field.getAnnotation(XOut.class);
                    return new StringColumn(name, required, unique, xOut == null ? null : xOut.value(),
                            maxLength == null ? null : maxLength.value(),
                            field != null && field.getAnnotation(Xhtml.class) != null);
                });
        generators.put(byte[].class,
                (Generator<byte[]>) (name, field, required, unique) -> {
                    val maxLength = field == null ? null : field.getAnnotation(MaxLength.class);
                    val encrypted = field == null ? null : field.getAnnotation(Encrypted.class);

                    return encrypted == null
                            ? new ByteArrayColumn(name, required, unique,
                            maxLength == null ? null : maxLength.value())
                            : new EncryptedColumn(name, required, unique,
                            maxLength == null ? null : maxLength.value());
                });
        generators.put(URI.class,
                (Generator<URI>) (name, _, required, unique) ->
                        new UriColumn(name, required, unique));
        generators.put(LocalizedString.class,
                (Generator<LocalizedString>) (name, _, required, unique) ->
                        new LocalizedStringColumn(name, required, unique));
        generators.put(LocalDateTime.class,
                (Generator<LocalDateTime>) (name, _, required, unique) ->
                        new LocalDateTimeColumn(name, required, unique));
        generators.put(LocalDate.class,
                (Generator<LocalDate>) (name, _, required, unique) ->
                        new LocalDateColumn(name, required, unique));
        generators.put(Duration.class,
                (Generator<Duration>) (name, _, required, unique) ->
                        new DurationColumn(name, required, unique));
        generators.put(Inet4Address.class,
                (Generator<Inet4Address>) (name, _, required, unique) ->
                        new Inet4AddressColumn(name, required, unique));
        //noinspection rawtypes
        generators.put(Class.class,
                (Generator<Class>) (name, _, required, unique) ->
                        new TypeColumn(name, required, unique));
    }

    private final Column<P> column;

    private SingleColumnProperty(final Class<P> type, final Field field, final Column<P> column) {
        super(type, field);
        this.column = column;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <O, P> SingleColumnProperty<O, P> $(final Field field) {
        val type = (Class<P>) field.getType();
        val required = isRequired(field);
        val unique = field.getAnnotation(Unique.class) != null;
        if (type.isArray() && !byte.class.equals(type.getComponentType())) {
            val column = $(field, type.getComponentType(), required, unique);
            return new SingleColumnProperty<>(type, field,
                    new ArrayColumn(column, field.getName(), required, unique));
        } else if (type.isEnum()) {
            return new SingleColumnProperty<>(type, field,
                    new EnumColumn(field.getName(), required, unique, type));
        } else if (field.getAnnotation(net.inetalliance.potion.annotations.Serial.class) != null) {
            return new SingleColumnProperty(type, field,
                    new SerialColumn(field.getName(), required, false));
        } else if (!type.isInterface()) {
            return new SingleColumnProperty(type, field, $(field, type, required, unique));
        }
        return null;
    }

    private static boolean isRequired(final Field field) {
        var required = false;
        for (val annotation : field.getAnnotations()) {
            if (Required.class.equals(annotation.annotationType())) {
                required = true;
            } else if (annotation.annotationType().getAnnotation(ConditionalRule.class) != null) {
                return false;
            }
        }
        return required;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <P> Column<P> $(final Class<P> type) {
        val generator = (Generator<P>) generators.get(type);
        if (generator == null) {
            if (type.isPrimitive()) {
                return $(Classes.getWrapper(type));
            } else if (Enum.class.isAssignableFrom(type)) {
                return new EnumColumn<>(null, false, false, (Class) type);
            }
            log.error(() -> "could not find generator for type %s".formatted(type.getName()));
            throw new PersistenceError(new NullPointerException());
        } else {
            return generator.gen(null, null, false, false);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <P> Column<P> $(final Field field, final Class<P> type, final boolean required,
                                  final boolean unique) {
        val generator = (Generator<P>) generators.get(type);
        if (generator == null) {
            if (type.isPrimitive()) {
                return $(field, Classes.getWrapper(type), required, unique);
            } else if (type.isEnum()) {
                return new EnumColumn<>(field.getName(), required, unique, (Class) type);
            }
            log.error(() -> "could not find generator for %s.%s of type %s".formatted(field.getDeclaringClass(),
                    field.getName(),
                    type.getName()));
            throw new PersistenceError(new NullPointerException());
        } else {
            return generator.gen(field.getName(), field, required, unique);
        }
    }

    @Override
    public P read(final String prefix, final ResultSet r, final DbVendor vendor, final O dest)
            throws SQLException {
        val p = column
                .read(prefix == null ? column.name : String.format("%s_%s", prefix, column.name), r,
                        vendor);
        return r.wasNull() ? null : p;
    }

    @Override
    public Property<O, P> rename(final String prefix, final boolean required, final boolean unique,
                                 final boolean forcePrefix) {
        return new SingleColumnProperty<>(type, field, column.rename(
                forcePrefix || field.getAnnotation(PrimaryKey.class) == null
                        ? String.format("%s_%s", prefix, column.name)
                        : prefix, required, unique));
    }

    @Override
    public void read(final O object, final Function<String, Object> cursor)
            throws Exception {
        val value = cursor.apply(field.getName());
        if (value != null) {
            Classes.set(object, field, value);
        }
    }

    @Override
    public void write(final O object, final BiConsumer<String, Object> cursor) {
        val p = apply(object);
        cursor.accept(field.getName(), p);
    }

    @Override
    public P read(final P previousValue, final O dest, final Map<String, Json> map) {
        val json = map.get(column.jsonName);
        return json == null ? previousValue : column.read(json, previousValue);
    }

    @Override
    public boolean containsProperty(final Map<String, Json> json) {
        return json.containsKey(column.jsonName);
    }

    @Override
    public Iterable<Class<? extends Enum<?>>> getReferencedEnums() {
        return Arrays.asList(column.enums);
    }

    @Override
    public Collection<Column<?>> getColumns() {
        return Set.of(column);
    }

    @Override
    public Iterable<JsonMap> recordDefinition() {
        return Collections.singleton(column.recordDefinition());
    }

    @Override
    public Iterable<String> getIndexes(final DbVendor vendor, final String table) {
        val index = field.getAnnotation(Indexed.class);
        val pk = field.getAnnotation(PrimaryKey.class);
        return index == null && pk == null
                ? Set.of()
                : Set.of(String.format("CREATE INDEX %s_%s ON %s USING %s(%s)", table, column.name,
                vendor.escapeEntity(table),
                index == null ? BTREE : index.value(), vendor.escapeEntity(column.name)));
    }

    @Override
    public Iterable<String> getSearchables(final DbVendor vendor) {
        val searchable = field.getAnnotation(Searchable.class);
        return searchable == null
                ? Set.of()
                : Set.of(String.format(
                "setweight(to_tsvector('english',coalesce(regexp_replace(text(new.%s),'[@.]',' ','g'),'')),'%s')",
                vendor.escapeEntity(column.name), searchable.value().name()));
    }

    @Override
    public String getForeignKeys(final DbVendor vendor, final Namer namer) {
        return null;
    }

    @Override
    public Iterable<BiConsumer<PreparedStatement, Integer>> setParameters(final DbVendor vendor,
                                                                          final O o) {
        return Set.of(column.bind(vendor, apply(o)));
    }

    @Override
    public Where getWhere(final Namer namer, final String table, final O o) {
        return column.getWhere(namer, table, apply(o));
    }

    @Override
    public Map<String, Json> toJson(final O o) {
        return Collections.singletonMap(column.jsonName, column.toJson(apply(o)));
    }

    @Override
    public P read(final DataInput in, final O dest)
            throws IOException {
        return in.readBoolean() ? null : column.read(in);
    }

    @Override
    public void write(final DataOutput out, final O o)
            throws IOException {
        val p = apply(o);
        out.writeBoolean(p == null);
        if (p != null) {
            column.write(out, p);
        }
    }

    @FunctionalInterface
    private interface Generator<P> {

        Column<P> gen(final String name, final Field field, final boolean required,
                      final boolean unique);
    }
}
