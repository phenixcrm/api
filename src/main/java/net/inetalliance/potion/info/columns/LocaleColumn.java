package net.inetalliance.potion.info.columns;

import net.inetalliance.potion.info.Column;
import net.inetalliance.sql.DbVendor;
import net.inetalliance.sql.Namer;
import net.inetalliance.sql.SqlType;
import net.inetalliance.sql.Where;
import net.inetalliance.types.json.Json;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;


public class LocaleColumn
    extends Column<java.util.Locale> {

  public LocaleColumn(final String name, final boolean required, final boolean unique) {
    super(name, required, unique, Locale.class);
  }

	@Override
  public Where getAutoCompleteWhere(final Namer namer, final String table, final String name,
      final String term) {
    final java.util.Locale value = java.util.Locale.forLanguageTag(term);
    return value == null ? null : Where.eq(table, name, value);
  }

  @Override
  public java.util.Locale[] newArray(final int size) {
    return new java.util.Locale[size];
  }

  @Override
  public java.util.Locale read(final String name, final ResultSet r, final DbVendor vendor)
      throws SQLException {
    final Locale locale = vendor.getEnum(r, Locale.class, name);
    return r.wasNull() ? null : locale.toLocale();
  }

  @Override
  public java.util.Locale read(final Json json, final java.util.Locale previousValue) {
    return java.util.Locale.forLanguageTag(json.toString());
  }

  @Override
  public java.util.Locale read(final DataInput in)
      throws IOException {
    return Locale.values()[in.readInt()].toLocale();
  }

  @Override
  public void write(final DataOutput out, final java.util.Locale locale)
      throws IOException {
    out.writeInt(Locale.fromLocale(locale).ordinal());
  }

  @Override
  public SqlType getType(final DbVendor vendor) {
    return vendor.getEnumType(Locale.class);
  }

  @Override
  public void bind(final PreparedStatement s, final DbVendor vendor, final Integer index,
      final java.util.Locale locale)
      throws SQLException {
    vendor.setParameter(s, index, Locale.fromLocale(locale));
  }

  public enum Locale {
    EN(java.util.Locale.US),
    ES(new java.util.Locale("es", "ES")),
    IT(java.util.Locale.ITALIAN),
    FR(java.util.Locale.FRENCH),
    DE(java.util.Locale.GERMAN);

    private final java.util.Locale locale;

    Locale(final java.util.Locale locale) {
      this.locale = locale;
    }

    static Locale fromLocale(final java.util.Locale locale) {
      if (locale == null) {
        return null;
      }
      for (final Locale localeEnum : Locale.values()) {
        if (localeEnum.locale.equals(locale)) {
          return localeEnum;
        }
      }
      return null;
    }

    java.util.Locale toLocale() {
      return locale;
    }
  }
}
