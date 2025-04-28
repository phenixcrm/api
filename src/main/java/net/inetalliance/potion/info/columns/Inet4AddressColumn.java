package net.inetalliance.potion.info.columns;

import net.inetalliance.potion.info.Column;
import net.inetalliance.potion.info.PersistenceError;
import net.inetalliance.sql.DbVendor;
import net.inetalliance.sql.Namer;
import net.inetalliance.sql.SqlType;
import net.inetalliance.sql.Where;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonString;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static com.ameriglide.phenix.core.Strings.isEmpty;

public class Inet4AddressColumn
    extends Column<Inet4Address> {

  public Inet4AddressColumn(final String name, final boolean required, final boolean unique) {
    super(name, required, unique);
  }

	@Override
  public Json toJson(final Inet4Address address) {
    return address == null ? null : new JsonString(address.getHostAddress());
  }

  @Override
  public Where getAutoCompleteWhere(final Namer namer, final String table, final String name,
      final String term) {
    throw new UnsupportedOperationException(
        "Autocomplete is not supported for date inet4 address columns");
  }

  @Override
  public Inet4Address[] newArray(final int size) {
    return new Inet4Address[size];
  }

  @Override
  public Inet4Address read(final String name, final ResultSet r, final DbVendor vendor)
      throws SQLException {
    return vendor.getInet4Address(r, name);
  }

  @Override
  public Inet4Address read(final Json json, final Inet4Address previousValue) {
    final String name = json.toString();
    try {
      return isEmpty(name) ? null : (Inet4Address) InetAddress.getByName(name);
    } catch (UnknownHostException e) {
      throw new PersistenceError(e);
    }
  }

  @Override
  public Inet4Address read(final DataInput in)
      throws IOException {
    final int size = in.readInt();
    final byte[] b = new byte[size];
    for (int i = 0; i < size; i++) {
      b[i] = in.readByte();
    }
    try {
      return (Inet4Address) Inet4Address.getByAddress(b);
    } catch (UnknownHostException e) {
      throw new PersistenceError(e);
    }
  }

  @Override
  public void write(final DataOutput out, final Inet4Address value)
      throws IOException {
    final byte[] b = value.getAddress();
    out.writeInt(b.length);
    out.write(b);
  }

  @Override
  public SqlType getType(final DbVendor vendor) {
    return vendor.getInet4AddressType();
  }

  @Override
  public void bind(final PreparedStatement s, final DbVendor vendor, final Integer index,
      final Inet4Address address)
      throws SQLException {
    vendor.setParameter(s, index, address);
  }
}
