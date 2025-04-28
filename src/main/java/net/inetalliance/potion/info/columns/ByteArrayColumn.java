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

public class ByteArrayColumn
    extends Column<byte[]> {

  public final Integer length;
  public static byte[] fromString(String value) {
    if (value == null)
      return null;
    final char[] in = value.toCharArray();
    final byte[] buf = new byte[in.length >> 1];

    for (int i = 0, x = 0; i < in.length >> 1; i++) {
      final char first = in[x++];
      final char second = in[x++];
      buf[i] = (byte)
              ((byte) (first - (first >= 'A' ? 'A' - 10 : '0') << 4)
                      + second - (second >= 'A' ? 'A' - 10 : '0'));

    }
    return buf;
  }

  private static final char[] hexChars = {'0', '1', '2', '3',
          '4', '5', '6', '7',
          '8', '9', 'A', 'B',
          'C', 'D', 'E', 'F',};

  public static String toString(byte[] value) {
    if (value == null)
      return null;
    try {
      final char[] buf = new char[(value.length << 1)];
      for (int i = 0, x = 0; i < value.length; i++) {
        buf[x++] = hexChars[value[i] >>> 4 & 0xf];
        buf[x++] = hexChars[value[i] & 0xf];
      }
      return new String(buf);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public ByteArrayColumn(final String name, final boolean required, final boolean unique,
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
    return fromString(r.getString(name));
  }

  @Override
  public byte[] read(final Json json, final byte[] previousValue) {
    return fromString(json.toString());
  }

  @Override
  public byte[] read(final DataInput in)
      throws IOException {
    return fromString(in.readUTF());
  }

  @Override
  public void write(final DataOutput out, final byte[] bytes)
      throws IOException {
    out.writeBytes(toString(bytes));
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
    s.setString(index, toString(bytes));
  }
}
