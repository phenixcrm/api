package net.inetalliance.potion.info.columns;

import net.inetalliance.potion.info.Column;
import net.inetalliance.sql.DbVendor;
import net.inetalliance.sql.Namer;
import net.inetalliance.sql.SqlType;
import net.inetalliance.sql.Where;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonExpression;
import net.inetalliance.types.json.JsonMap;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DoubleColumn
    extends Column<Double> {

  private final String numberFormat;

  public DoubleColumn(final String name, final boolean required, final boolean unique,
      final String numberFormat) {
    super(name, required, unique);
    this.numberFormat = numberFormat;
  }

	@Override
  public Where getAutoCompleteWhere(final Namer namer, final String table, final String name,
      final String term) {
    try {
      return Where.eq(table, name, Double.parseDouble(term));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  @Override
  public Double[] newArray(final int size) {
    return new Double[size];
  }

  @Override
  public Double read(final String name, final ResultSet r, final DbVendor vendor)
      throws SQLException {
    return r.getDouble(name);
  }

  @Override
  public Double read(final Json json, final Double previousValue) {
    return Double.valueOf(json.toString());
  }

  @Override
  public Double read(final DataInput in)
      throws IOException {
    return in.readDouble();
  }

  @Override
  public void write(final DataOutput out, final Double value)
      throws IOException {
    out.writeDouble(value);
  }

  @Override
  public SqlType getType(final DbVendor vendor) {
    return SqlType.sqlDouble;
  }

  @Override
  public void bind(final PreparedStatement s, final DbVendor vendor, final Integer index,
      final Double value)
      throws SQLException {
    s.setDouble(index, value);
  }

  @Override
  protected void recordDefinitionAdditional(final JsonMap definition) {
    definition.put("type", "float");
    definition.put("align", "right");
    if (numberFormat != null) {
      definition.put("renderer",
          new JsonExpression(String.format("Ext.util.Format.numberRenderer('%s')", numberFormat)));
    }
  }
}
