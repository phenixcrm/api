package net.inetalliance.potion.info;

import net.inetalliance.sql.DbVendor;
import net.inetalliance.sql.Namer;
import net.inetalliance.sql.SqlType;
import net.inetalliance.sql.Where;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;
import net.inetalliance.types.json.JsonString;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.joining;

public abstract class Column<C> {

  private static final Pattern underscore = Pattern.compile("_");
  public final String name;
  public final String jsonName;
  public final boolean required;
  public final boolean unique;
  public Class<? extends Enum<?>>[] enums;

  @SafeVarargs
  public Column(final String name, final boolean required, final boolean unique,
      final Class<? extends Enum<?>>... enums) {
    this.name = name;
    if (this.name == null) {
      this.jsonName = null;
    } else {
      final String[] split = underscore.split(name);
      this.jsonName = split[split.length - 1];
    }
    this.required = required;
    this.unique = unique;
    this.enums = enums;
  }

  public static Column<?> rename(final Column<?> column, final String prefix, final boolean required,
      final boolean unique) {
    return column.rename(String.format("%s_%s", prefix, column.name), required, unique);
  }

  public Column<C> rename(final String alias, final boolean required, final boolean unique) {
    return new Column<>(alias, required, unique, enums) {
      @Override
      public SqlType getType(final DbVendor vendor) {
        final SqlType type = Column.this.getType(vendor);
        return type.equals(vendor.getSerialType()) ? SqlType.sqlInteger : type;
      }

      @Override
      protected void recordDefinitionAdditional(final JsonMap definition) {
        Column.this.recordDefinitionAdditional(definition);
      }

      @Override
      public Json toJson(final C c) {
        return Column.this.toJson(c);
      }

      @Override
      public Where getAutoCompleteWhere(final Namer namer, final String table, final String name,
          final String term) {
        return Column.this.getAutoCompleteWhere(namer, table, name, term);
      }

      @Override
      public C[] newArray(final int size) {
        return Column.this.newArray(size);
      }

      @Override
      public C read(final String name, final ResultSet r, final DbVendor vendor)
          throws SQLException {
        return Column.this.read(name, r, vendor);
      }

      @Override
      public C read(final Json json, final C previousValue) {
        return Column.this.read(json, previousValue);
      }

      @Override
      public void bind(final PreparedStatement s, final DbVendor vendor, final Integer index,
          final C c)
          throws SQLException {
        Column.this.bind(s, vendor, index, c);
      }

      @Override
      public Where getWhere(final Namer namer, final String table, final C c) {
        return super.getWhere(namer, table, c);
      }

      @Override
      public C read(final DataInput in)
          throws IOException {
        return Column.this.read(in);
      }

      @Override
      public void write(final DataOutput out, final C c)
          throws IOException {
        Column.this.write(out, c);
      }
    };
  }

  public Json toJson(final C c) {
    return c == null ? null : new JsonString(c.toString());
  }

  public abstract Where getAutoCompleteWhere(final Namer namer, final String table,
      final String name,
      final String term);

  public abstract C[] newArray(final int size);

  public abstract C read(final String name, final ResultSet r, final DbVendor vendor)
      throws SQLException;

  public abstract C read(final Json json, final C previousValue);

  public Where getWhere(final Namer namer, final String table, final C c) {
    return Where.eq(table, name, c);
  }

  public abstract C read(final DataInput in)
      throws IOException;

  public abstract void write(final DataOutput out, final C c)
      throws IOException;

  public BiConsumer<PreparedStatement, Integer> bind(final DbVendor vendor, final C c) {
    return (s, index) -> {
      try {
        if (c == null) {
          s.setNull(index, getType(vendor).javaType);
        } else {
          Column.this.bind(s, vendor, index, c);
        }
      } catch (SQLException e) {
        throw new PersistenceError(e);
      }
    };
  }

  public abstract SqlType getType(final DbVendor vendor);

  public abstract void bind(final PreparedStatement s, final DbVendor vendor, final Integer index,
      final C c)
      throws SQLException;

  public final JsonMap recordDefinition() {
    final JsonMap definition = new JsonMap();
    definition.put("name", name.replaceAll("_", "."));
    if (required) {
      definition.put("allowBlank", false);
    }
    recordDefinitionAdditional(definition);
    return definition;
  }

  protected void recordDefinitionAdditional(final JsonMap definition) {
    // can be overridden
  }

  public String toDefinition(final DbVendor vendor) {
      return "%s %s".formatted(vendor.escapeEntity(name), getType(vendor).name);
  }

  public String toDefinition(final DbVendor vendor, final Collection<String> checks) {
    final StringBuilder def = new StringBuilder(32);
    def.append(vendor.escapeEntity(name));
    def.append(' ');
    def.append(getType(vendor).name);
    if (required) {
      def.append(" NOT NULL");
    }
    if (unique) {
      def.append(" UNIQUE");
    }
    if (checks != null && !checks.isEmpty()) {
      def.append(checks.stream().collect(joining(")AND(", " CHECK((", "))")));
    }

    return def.toString();
  }

  public String where(final DbVendor vendor) {
    return String.format("%s=?", vendor.escapeEntity(name));
  }
}
