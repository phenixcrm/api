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
import java.util.Locale;

public class ParsingLocaleColumn
    extends Column<java.util.Locale> {

  public ParsingLocaleColumn(final String name, final boolean required, final boolean unique) {
    super(name, required, unique);
  }

	@Override
  public Where getAutoCompleteWhere(final Namer namer, final String table, final String name,
      final String term) {
    final java.util.Locale value = java.util.Locale.forLanguageTag(term);
    return value == null ? null : Where.eq(table, name, term);
  }

  @Override
  public Locale[] newArray(final int size) {
    return new Locale[size];
  }

  @Override
  public Locale read(final String name, final ResultSet r, final DbVendor vendor)
      throws SQLException {
    final String raw = r.getString(name);
    return r.wasNull() ? null : java.util.Locale.forLanguageTag(raw);
  }

  @Override
  public java.util.Locale read(final Json json, final Locale previousValue) {
    return java.util.Locale.forLanguageTag(json.toString());
  }

  @Override
  public Locale read(final DataInput in)
      throws IOException {
    return java.util.Locale.forLanguageTag(in.readUTF());
  }

  @Override
  public void write(final DataOutput out, final Locale locale)
      throws IOException {
    out.writeUTF(locale.toString());
  }

  @Override
  public SqlType getType(final DbVendor vendor) {
    return SqlType.sqlText;
  }

  @Override
  public void bind(final PreparedStatement s, final DbVendor vendor, final Integer index,
      final java.util.Locale locale)
      throws SQLException {
    s.setString(index, locale.toString());
  }
}
