package net.inetalliance.potion.info.columns;

import net.inetalliance.potion.info.Column;
import net.inetalliance.sql.DbVendor;
import net.inetalliance.sql.Namer;
import net.inetalliance.sql.SqlType;
import net.inetalliance.sql.Where;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonBoolean;
import net.inetalliance.types.json.JsonMap;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;

public class BooleanColumn
    extends Column<Boolean> {

  private final Boolean defaultTo;

  public BooleanColumn(final String name, final boolean required, final boolean unique,
      final Boolean defaultTo) {
    super(name, required, unique);
    this.defaultTo = defaultTo;
  }

	@Override
  public Json toJson(final Boolean value) {
    return JsonBoolean.$(value);
  }

  @Override
  public Where getAutoCompleteWhere(final Namer namer, final String table, final String name,
      final String term) {
    throw new UnsupportedOperationException("Autocomplete is not supported for boolean columns");
  }

  @Override
  public Boolean[] newArray(final int size) {
    return new Boolean[size];
  }

  @Override
  public Boolean read(final String name, final ResultSet r, final DbVendor vendor)
      throws SQLException {
    return r.getBoolean(name);
  }

  @Override
  public Boolean read(final Json json, final Boolean previousValue) {
    return json.toBoolean();
  }

  @Override
  public Boolean read(final DataInput in)
      throws IOException {
    return in.readBoolean();
  }

  @Override
  public void write(final DataOutput out, final Boolean value)
      throws IOException {
    out.writeBoolean(value);
  }

  @Override
  public SqlType getType(final DbVendor vendor) {
    return SqlType.sqlBoolean;
  }

  @Override
  public void bind(final PreparedStatement s, final DbVendor vendor, final Integer index,
      final Boolean value)
      throws SQLException {
    s.setBoolean(index, value);
  }

  @Override
  protected void recordDefinitionAdditional(final JsonMap definition) {
    definition.put("type", "boolean");
  }

  @Override
  public String toDefinition(final DbVendor vendor, final Collection<String> checks) {
    final String definition = super.toDefinition(vendor, checks);
    return defaultTo == null ? definition : String.format("%s DEFAULT %s", definition, defaultTo);
  }
}
