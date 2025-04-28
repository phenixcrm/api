package net.inetalliance.potion.info.columns;

import net.inetalliance.potion.info.Column;
import net.inetalliance.sql.DbVendor;
import net.inetalliance.sql.Namer;
import net.inetalliance.sql.SqlType;
import net.inetalliance.sql.Where;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.security.KeyRing;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class EncryptedColumn
    extends Column<byte[]> {

  public final Integer length;

  public EncryptedColumn(final String name, final boolean required, final boolean unique,
      final Integer length) {
    super(name, required, unique);
    this.length = length;
  }

	@Override
  public Where getAutoCompleteWhere(final Namer namer, final String table, final String name,
      final String term) {
    return Where.like(table, name, String.format("%%%s%%", term), false);
  }

  @Override
  public byte[][] newArray(final int size) {
    return new byte[size][];
  }

  @Override
  public byte[] read(final String name, final ResultSet r, final DbVendor vendor)
      throws SQLException {
    return ByteArrayColumn.fromString(r.getString(name));
  }

  @Override
  public byte[] read(final Json json, final byte[] previousValue) {
    return KeyRing.$(json.toString());
  }

  @Override
  public byte[] read(final DataInput in)
      throws IOException {
    return ByteArrayColumn.fromString(in.readUTF());
  }

  @Override
  public void write(final DataOutput out, final byte[] bytes)
      throws IOException {
    out.writeUTF(ByteArrayColumn.toString(bytes));
  }

  @Override
  public SqlType getType(final DbVendor vendor) {
    return length == null
        ? SqlType.sqlText
        : new SqlType(SqlType.sqlVarchar.javaType,
            String.format("%s(%d)", SqlType.sqlVarchar.name, length));
  }

  @Override
  public void bind(final PreparedStatement s, final DbVendor vendor, final Integer index,
      final byte[] bytes)
      throws SQLException {
    s.setString(index, ByteArrayColumn.toString(bytes));
  }
}
