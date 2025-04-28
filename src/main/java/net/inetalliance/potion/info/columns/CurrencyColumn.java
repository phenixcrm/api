package net.inetalliance.potion.info.columns;

import net.inetalliance.potion.info.Column;
import net.inetalliance.sql.DbVendor;
import net.inetalliance.sql.Namer;
import net.inetalliance.sql.SqlType;
import net.inetalliance.sql.Where;
import net.inetalliance.types.Currency;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonExpression;
import net.inetalliance.types.json.JsonMap;
import net.inetalliance.types.json.JsonString;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;

public class CurrencyColumn
    extends Column<Currency> {

  public CurrencyColumn(final String name, final boolean required, final boolean unique) {
    super(name, required, unique);
  }

	@Override
  public Json toJson(final Currency currency) {
    return new JsonString(currency == null ? null : currency.toString());
  }

  @Override
  public Where getAutoCompleteWhere(final Namer namer, final String table, final String name,
      final String term) {
    try {
      return Where.eq(table, name, Currency.parse(term));
    } catch (ParseException e) {
      return null;
    }
  }

  @Override
  public Currency[] newArray(final int size) {
    return new Currency[size];
  }

  @Override
  public Currency read(final String name, final ResultSet r, final DbVendor vendor)
      throws SQLException {
    final long value = r.getLong(name);
    return r.wasNull() ? null : Currency.getInstance(value);
  }

  @Override
  public Currency read(final Json json, final Currency previousValue) {
    try {
      return Currency.parse(json.toString());
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Currency read(final DataInput in)
      throws IOException {
    return Currency.getInstance(in.readLong());
  }

  @Override
  public void write(final DataOutput out, final Currency currency)
      throws IOException {
    out.writeLong(currency.getValue());
  }

  @Override
  public SqlType getType(final DbVendor vendor) {
    return SqlType.sqlLong;
  }

  @Override
  public void bind(final PreparedStatement s, final DbVendor vendor, final Integer index,
      final Currency value)
      throws SQLException {
    s.setLong(index, value.getValue());
  }

  @Override
  protected void recordDefinitionAdditional(final JsonMap definition) {
    definition.put("type", "float");
    definition.put("align", "right");
    definition.put("renderer", new JsonExpression("Ext.util.Format.usMoney"));
  }
}
