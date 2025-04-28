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

public class LongColumn
    extends Column<Long> {

  public LongColumn(final String name, final boolean required, final boolean unique) {
    super(name, required, unique);
  }

	@Override
  public Where getAutoCompleteWhere(final Namer namer, final String table, final String name,
      final String term) {
    try {
      return Where.eq(table, name, Long.parseLong(term));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  @Override
  public Long[] newArray(final int size) {
    return new Long[size];
  }

  @Override
  public Long read(final String name, final ResultSet r, final DbVendor vendor)
      throws SQLException {
    return r.getLong(name);
  }

  @Override
  public Long read(final Json json, final Long previousValue) {
    return json.toLong();
  }

  @Override
  public Long read(final DataInput in)
      throws IOException {
    return in.readLong();
  }

  @Override
  public void write(final DataOutput out, final Long value)
      throws IOException {
    out.writeLong(value);
  }

  @Override
  public SqlType getType(final DbVendor vendor) {
    return SqlType.sqlLong;
  }

  @Override
  public void bind(final PreparedStatement s, final DbVendor vendor, final Integer index,
      final Long value)
      throws SQLException {
    s.setLong(index, value);
  }

  @Override
  protected void recordDefinitionAdditional(final JsonMap definition) {
    definition.put("type", "int");
  }
}
