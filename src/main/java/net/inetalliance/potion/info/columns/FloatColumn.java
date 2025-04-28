package net.inetalliance.potion.info.columns;

import net.inetalliance.potion.info.Column;
import net.inetalliance.sql.DbVendor;
import net.inetalliance.sql.Namer;
import net.inetalliance.sql.SqlType;
import net.inetalliance.sql.Where;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class FloatColumn
    extends Column<Float> {

  public FloatColumn(final String name, final boolean required, final boolean unique) {
    super(name, required, unique);
  }

	@Override
  public Where getAutoCompleteWhere(final Namer namer, final String table, final String name,
      final String term) {
    try {
      return Where.eq(table, name, Float.parseFloat(term));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  @Override
  public Float[] newArray(final int size) {
    return new Float[size];
  }

  @Override
  public Float read(final String name, final ResultSet r, final DbVendor vendor)
      throws SQLException {
    return r.getFloat(name);
  }

  @Override
  public Float read(final Json json, final Float previousValue) {
    return Float.valueOf(json.toString());
  }

  @Override
  public Float read(final DataInput in)
      throws IOException {
    return in.readFloat();
  }

  @Override
  public void write(final DataOutput out, final Float value)
      throws IOException {
    out.writeFloat(value);
  }

  @Override
  public SqlType getType(final DbVendor vendor) {
    return SqlType.sqlFloat;
  }

  @Override
  public void bind(final PreparedStatement s, final DbVendor vendor, final Integer index,
      final Float value)
      throws SQLException {
    s.setFloat(index, value);
  }

  @Override
  protected void recordDefinitionAdditional(final JsonMap definition) {
    definition.put("type", "float");
    definition.put("align", "right");
  }
}
