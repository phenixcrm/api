package net.inetalliance.potion.info;

import com.ameriglide.phenix.core.Iterables;
import com.ameriglide.phenix.core.Log;
import net.inetalliance.potion.Locator;
import net.inetalliance.potion.obj.ExternalFile;
import net.inetalliance.sql.DbVendor;
import net.inetalliance.sql.Namer;
import net.inetalliance.sql.Where;
import net.inetalliance.types.annotations.Required;
import net.inetalliance.types.annotations.Unique;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;
import net.inetalliance.types.www.ContentType;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.regex.Pattern.compile;
import static java.util.stream.Collectors.toList;

public class SubObjectProperty<O, P>
    extends Property<O, P> {

  private static final Log log = new Log();
  public final boolean external;
  protected final String prefix;
  private final boolean required;
  private final boolean unique;

  SubObjectProperty(final Class<P> type, final Field field) {
    this(type, field, field.getName(), field.getAnnotation(Required.class) != null,
        field.getAnnotation(Unique.class) != null);
  }

  SubObjectProperty(final Class<P> type, final Field field, final String prefix,
      final boolean required,
      final boolean unique) {
    super(type, field);
    this.prefix = prefix;
    external = ExternalFile.class.isAssignableFrom(type);
    this.required = required;
    this.unique = unique;
  }

  @SuppressWarnings({"unchecked"})
  static <O, P> SubObjectProperty<O, P> $A(final Field field) {
    return new SubObjectProperty<>((Class<P>) field.getType().getComponentType(), field);
  }

  @SuppressWarnings({"unchecked"})
  public static <O, P> SubObjectProperty<O, P> $(final Field field) {
    return new SubObjectProperty<>((Class<P>) field.getType(), field);
  }

  @Override
  public void clone(final O src, final O dest)
      throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
    final P srcObj = apply(src);
    if (srcObj == null) {
      field.set(dest, null);
    } else {
      final P destObj = construct(dest);
        log.trace(()->"Constructed %s subobject clone for %s.".formatted( type.getSimpleName(), field.getName()));
      Locator.clone(srcObj, destObj);
      field.set(dest, destObj);
    }
  }

  P construct(final O dest)
      throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    if (external) {
      return type.getConstructor(Object.class, String.class).newInstance(dest, field.getName());
    } else {
      // try looking for a constructor that takes the owner object
      if (dest != null) {
        for (final Class<?> paramType : Iterables.assignable(dest.getClass())) {
          try {
            return type.getConstructor(paramType).newInstance(dest);
          } catch (final Exception e) {
            // nevermind. keep trying
          }
          try {
            return type.getConstructor(paramType, String.class).newInstance(dest, field.getName());
          } catch (final Exception e) {
            // nevermind. keep trying
          }
        }
      }
      return type.getDeclaredConstructor().newInstance();
    }
  }

  @Override
  public P read(final String prefix, final ResultSet r, final DbVendor vendor, final O dest)
      throws SQLException {
    try {
      final P p = construct(dest);
      final boolean anyNonNull = getProperties().map(property -> {
        try {
          Object value = property.read(prefix, r, vendor, p);
          property.field.set(p, value);
          return value != null;
        } catch (SQLException | IllegalAccessException e) {
          throw new PersistenceError(e);
        }
      }).reduce(false, (a, v) -> a || v);
      return anyNonNull ? readResolve(p) : null;
    } catch (final Exception e) {
      throw new PersistenceError(e);
    }
  }

  @Override
  public Property<O, P> rename(final String prefix, final boolean required, final boolean unique,
      final boolean forcePrefix) {
    return new SubObjectProperty<>(type, field,
        this.prefix == null ? prefix : format("%s_%s", prefix, this.prefix),
        required, unique);
  }

  @Override
  public void read(final O object, final Function<String, Object> cursor)
      throws Exception {
    final P subobject = apply(object);
    if (subobject != null) {
      final String prefix = field.getName() + '_';
      Info.$(subobject).read(subobject, name -> cursor.apply(prefix + name));
    }
  }

  @Override
  public void write(final O object, final BiConsumer<String, Object> cursor) {
    final P subobject = apply(object);
    if (subobject != null) {
      final String prefix = field.getName() + '_';
      Info.$(subobject).write(subobject, (name, value) -> cursor.accept(prefix + name, value));
    }
  }

  @Override
  public P read(P previousValue, final O dest, final Map<String, Json> map) {
    try {
      if (previousValue == null) {
        previousValue = construct(dest);
      }
      if (external) {
        final JsonMap values = (JsonMap) map.get(field.getName());
        if (values == null) {
          return null;
        } else {
          final ExternalFile previous = (ExternalFile) previousValue;
          final String repositoryPath = values.get("repository");
          if (repositoryPath != null) {
            final File repository = new File(repositoryPath);
            final String filename = values.get("file");
            if (filename == null) {
              previous.delete(repository);
              return null;
            } else {
              previous.set(repository, new File(filename),
                  ContentType.parse(values.get("contentType")));
            }
          }
          return readResolve(previousValue);
        }
      } else {
        JsonMap values = (JsonMap) map.get(field.getName());
        if (values == null) {
          values = new JsonMap();
          final Pattern property = compile(format("%s\\.(.*)", field.getName()));
          for (final Map.Entry<String, Json> entry : map.entrySet()) {
            final Matcher matcher = property.matcher(entry.getKey());
            if (matcher.matches()) {
              values.put(matcher.group(1), entry.getValue());
            }
          }
        }
        final P finalPreviousValue = previousValue;
        final JsonMap finalValues = values;
        final boolean anyNonNull =
            getProperties().map(p -> p.setIf(finalPreviousValue, finalValues) != null)
                .reduce(false, (a, v) -> a || v);
        return anyNonNull ? readResolve(previousValue) : null;
      }
    } catch (final Exception e) {
      throw new PersistenceError(e);
    }
  }

  @Override
  public boolean containsProperty(final Map<String, Json> json) {
    return json.containsKey(field.getName()) || json.keySet()
        .stream()
        .anyMatch(s -> s.startsWith(field.getName() + '.'));
  }

  @Override
  public Iterable<Class<? extends Enum<?>>> getReferencedEnums() {
    return () -> getProperties().map(Property::getReferencedEnums).flatMap(Iterables::stream)
        .iterator();
  }

  @Override
  public Iterable<Column<?>> getColumns() {
    return () -> getProperties().map(Property::getColumns).flatMap(Iterables::stream).iterator();
  }

  protected Stream<Property<P, ?>> getProperties() {
    return Info.$(type).properties().map(c -> c.rename(prefix, this.required, this.unique, false));
  }

  @Override
  public Iterable<JsonMap> recordDefinition() {
    return () -> getProperties().map(Property::recordDefinition).flatMap(Iterables::stream).iterator();
  }

  @Override
  public Iterable<String> getIndexes(final DbVendor vendor, final String table) {
    return () -> getProperties().map(p -> p.getIndexes(vendor, table)).flatMap(Iterables::stream)
        .iterator();
  }

  @Override
  public Iterable<String> getSearchables(final DbVendor vendor) {
    return () -> getProperties().map(p -> p.getSearchables(vendor)).flatMap(Iterables::stream)
        .iterator();
  }

  @Override
  public String getForeignKeys(final DbVendor vendor, final Namer namer) {

    return null;
  }

  @Override
  public Iterable<BiConsumer<PreparedStatement, Integer>> setParameters(final DbVendor vendor,
      final O o) {
    return () -> getProperties().map(p -> p.setParameters(vendor, apply(o))).flatMap(Iterables::stream)
        .iterator();
  }

  @Override
  public Where getWhere(final Namer namer, final String table, final O o) {
    return Where
        .and(getProperties().map(p -> p.getWhere(namer, table, apply(o))).collect(toList()));
  }

  @Override
  public Map<String, Json> toJson(final O o) {
    final JsonMap map = new JsonMap();

    getProperties().map(p -> p.toJson(apply(o))).forEach(map::putAll);
    return JsonMap.singletonMap(field.getName(), map);
  }

  @Override
  public P read(final DataInput in, final O dest) {
    try {
      final P p = construct(dest);
      final boolean anyNonNull = getProperties().map(property -> {
        try {
          Object value = property.read(in, p);
          property.field.set(p, value);
          return value != null;
        } catch (Exception e) {
          throw new PersistenceError(e);
        }
      }).reduce(false, (a, v) -> a || v);
      return anyNonNull ? readResolve(p) : null;
    } catch (final Exception e) {
      throw new PersistenceError(e);
    }
  }

  protected P readResolve(final P p) {
    return p;
  }

  @Override
  public void write(final DataOutput out, final O o)
      throws IOException {
    final P p = apply(o);
    writeInstance(out, p);
  }

  private void writeInstance(final DataOutput out, final P p) {

    getProperties().forEach(property -> {
      try {
        property.write(out, p);
      } catch (final IOException e) {
        throw new PersistenceError(e);
      }
    });
  }
}
