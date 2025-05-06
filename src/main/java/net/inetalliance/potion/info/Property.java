package net.inetalliance.potion.info;

import com.ameriglide.phenix.core.Classes;
import com.ameriglide.phenix.core.Iterables;
import lombok.val;
import net.inetalliance.potion.annotations.Autocompletable;
import net.inetalliance.potion.annotations.ForeignKey;
import net.inetalliance.potion.annotations.ManyToMany;
import net.inetalliance.potion.annotations.PrimaryKey;
import net.inetalliance.potion.obj.ExternalFile;
import net.inetalliance.sql.DbVendor;
import net.inetalliance.sql.Namer;
import net.inetalliance.sql.Where;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;
import net.inetalliance.validation.properties.FieldProperty;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toList;

public abstract class Property<O, P>
        implements Function<O, P> {

    public final Class<P> type;
    public final Field field;

    protected Property(final Class<P> type, final Field field) {
        this.type = type;
        this.field = field;
        field.setAccessible(true);
    }

    public static <O> Predicate<Property<O, ?>> modified(O updated, O old) {
        return p -> !Objects.equals(p.apply(updated), p.apply(old));

    }

    public String getName() {
        return field.getName();
    }

    public boolean isExternal() {
        return ExternalFile.class.isAssignableFrom(field.getType());
    }

    public boolean isManyToMany() {
        return field.isAnnotationPresent(ManyToMany.class);
    }

    public boolean isGenerated() {
        return field.isAnnotationPresent(net.inetalliance.potion.annotations.Generated.class);
    }

    public boolean isKey() {
        return field.isAnnotationPresent(PrimaryKey.class);
    }

    public boolean isForeignKey() {
        return field.isAnnotationPresent(ForeignKey.class);
    }

    public boolean isAutocompletable() {
        return field.isAnnotationPresent(Autocompletable.class);
    }

    @Override
    public int hashCode() {
        var result = type.hashCode();
        result = 31 * result + field.hashCode();
        return result;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof Property<?, ?> p) {
            return field.equals(p.field) && type.equals(p.type);
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("%s.%s [%s]", field.getDeclaringClass().getSimpleName(), field.getName(),
                type.getSimpleName());
    }

    public void clone(final O src, final O dest)
            throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        field.set(dest, apply(src));
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public P apply(final O o) {
        try {
            if (o == null) {
                return null;
            }
            val value = field.get(o);
            if (value == null) {
                return null;
            } else if (type.isInstance(value)) {
                return (P) value;
            } else if (type.isPrimitive() && Classes.getWrapper(type).isInstance(value)) {
                return (P) value;
            } else {
                throw new ClassCastException();
            }
        } catch (IllegalAccessException e) {
            throw new PersistenceError(e);
        }
    }

    public P read(final ResultSet r, final DbVendor vendor, final O dest)
            throws SQLException {
        return read(null, r, vendor, dest);
    }

    protected abstract P read(final String prefix, final ResultSet r, final DbVendor vendor,
                              final O dest)
            throws SQLException;

    public Property<O, P> rename(final String prefix, final boolean required, final boolean unique,
                                 final boolean forcePrefix) {
        return new Property<>(type, field) {
            @Override
            public void read(final O object, final Function<String, Object> cursor)
                    throws Exception {
                Property.this.read(object, cursor);
            }

            @Override
            public void write(final O object, final BiConsumer<String, Object> cursor) {
                Property.this.write(object, cursor);
            }

            @Override
            public P read(final String prefix, final ResultSet r, final DbVendor vendor, final O dest)
                    throws SQLException {
                return Property.this.read(prefix, r, vendor, dest);
            }

            @Override
            public P read(final P previousValue, final O dest, final Map<String, Json> json) {
                return Property.this.read(previousValue, dest, json);
            }

            @Override
            public boolean containsProperty(final Map<String, Json> json) {
                return Property.this.containsProperty(json);
            }

            @Override
            public Iterable<Class<? extends Enum<?>>> getReferencedEnums() {
                return Property.this.getReferencedEnums();
            }

            @Override
            public Iterable<Column<?>> getColumns() {
                return Iterables.stream(Property.this.getColumns()).map(c -> c.rename(prefix, required, unique)).collect(toList());

            }

            @Override
            public Iterable<JsonMap> recordDefinition() {
                return Property.this.recordDefinition();
            }

            @Override
            public Iterable<String> getIndexes(final DbVendor vendor, final String table) {
                return Property.this.getIndexes(vendor, table);
            }

            @Override
            public Iterable<String> getSearchables(final DbVendor vendor) {
                return Property.this.getSearchables(vendor);
            }

            @Override
            public String getForeignKeys(final DbVendor vendor, final Namer namer) {
                return Property.this.getForeignKeys(vendor, namer);
            }

            @Override
            public Iterable<BiConsumer<PreparedStatement, Integer>> setParameters(final DbVendor vendor,
                                                                                  final O o) {
                return Property.this.setParameters(vendor, o);
            }

            @Override
            public Where getWhere(final Namer namer, final String table, final O o) {
                return Property.this.getWhere(namer, table, o);
            }

            @Override
            public Map<String, Json> toJson(final O o) {
                return Property.this.toJson(o);
            }

            @Override
            public P read(final DataInput in, final O dest)
                    throws IOException {
                return Property.this.read(in, dest);
            }

            @Override
            public void write(final DataOutput out, final O o)
                    throws IOException {
                Property.this.write(out, o);
            }
        };
    }

    public abstract void read(final O object, final Function<String, Object> cursor)
            throws Exception;

    public abstract void write(final O object, final BiConsumer<String, Object> cursor);

    public abstract P read(final P previousValue, final O dest, final Map<String, Json> json);

    public abstract boolean containsProperty(final Map<String, Json> json);

    public abstract Iterable<Class<? extends Enum<?>>> getReferencedEnums();

    public abstract Iterable<Column<?>> getColumns();

    public abstract Iterable<JsonMap> recordDefinition();

    public abstract Iterable<String> getIndexes(final DbVendor vendor, final String table);

    public abstract Iterable<String> getSearchables(final DbVendor vendor);

    public abstract String getForeignKeys(final DbVendor vendor, final Namer namer);

    public abstract Iterable<BiConsumer<PreparedStatement, Integer>> setParameters(
            final DbVendor vendor, final O o);

    public abstract Where getWhere(final Namer namer, final String table, final O o);

    public abstract Map<String, Json> toJson(final O o);

    public abstract P read(final DataInput in, final O dest)
            throws IOException;

    public abstract void write(final DataOutput out, O o)
            throws IOException;

    public P setIf(final O dest, final Map<String, Json> json) {
        if (containsProperty(json)) {
            try {
                val previousValue = type.cast(field.get(dest));
                val value = read(previousValue, dest, json);
                field.set(dest, value);
                return value;
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        } else {
            return null;
        }
    }

    public net.inetalliance.validation.properties.Property<?> toValidationProperty() {
        return new FieldProperty<>(field);
    }

}
