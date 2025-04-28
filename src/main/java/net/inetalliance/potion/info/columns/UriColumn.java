package net.inetalliance.potion.info.columns;

import net.inetalliance.potion.info.Column;
import net.inetalliance.potion.info.PersistenceError;
import net.inetalliance.sql.DbVendor;
import net.inetalliance.sql.Namer;
import net.inetalliance.sql.SqlType;
import net.inetalliance.sql.Where;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UriColumn
    extends Column<URI> {

  public UriColumn(final String name, final boolean required, final boolean unique) {
    super(name, required, unique);
  }

	@Override
  public Where getAutoCompleteWhere(final Namer namer, final String table, final String name,
      final String term) {
    return Where.like(table, name, term, false);
  }

  @Override
  public URI[] newArray(final int size) {
    return new URI[size];
  }

  @Override
  public URI read(final String name, final ResultSet r, final DbVendor vendor)
      throws SQLException {
    final String s = r.getString(name);
    try {
      return r.wasNull() ? null : new URI(s);
    } catch (final URISyntaxException e) {
      throw new PersistenceError(e);
    }
  }

  @Override
  public URI read(final Json json, final URI previousValue) {
    try {
      return new URI(json.toString());
    } catch (final URISyntaxException e) {
      throw new PersistenceError(e);
    }
  }

  @Override
  public URI read(final DataInput in)
      throws IOException {
    try {
      return new URI(in.readUTF());
    } catch (final URISyntaxException e) {
      throw new PersistenceError(e);
    }
  }

  @Override
  public void write(final DataOutput out, final URI uri)
      throws IOException {
    out.writeUTF(uri.toString());
  }

  @Override
  public SqlType getType(final DbVendor vendor) {
    return SqlType.sqlVarchar;
  }

  @Override
  public void bind(final PreparedStatement s, final DbVendor vendor, final Integer index,
      final URI uri)
      throws SQLException {
    s.setString(index, uri.toString());
  }

  @Override
  protected void recordDefinitionAdditional(final JsonMap definition) {
    definition.put("type", "string");
  }
}
