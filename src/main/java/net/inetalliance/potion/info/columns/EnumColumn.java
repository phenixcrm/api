package net.inetalliance.potion.info.columns;

import com.ameriglide.phenix.core.Log;
import net.inetalliance.potion.Locator;
import net.inetalliance.potion.info.Column;
import net.inetalliance.sql.DbVendor;
import net.inetalliance.sql.Namer;
import net.inetalliance.sql.SqlType;
import net.inetalliance.sql.Where;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonString;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

import static com.ameriglide.phenix.core.Strings.isEmpty;

public class EnumColumn<E extends Enum<E>>
    extends Column<E> {

  private static final Log log = new Log();
  public final Class<E> type;

  public EnumColumn(final String name, final boolean required, final boolean unique,
      final Class<E> type) {
    super(name, required, unique, type);
    try {
      Locator.register(type);
    } catch (Throwable e) {
      // hack to ignore ClassNotFoundException in beejax message server -Erik, 2011-10-04
      log.error(()->"%s when trying to register type %s".formatted( e.getClass().getSimpleName(),
          type.getSimpleName()));
    }
    this.type = type;
  }

	@Override
  public Json toJson(final E e) {
    return e == null ? null : new JsonString(e.name());
  }

  @Override
  public Where getAutoCompleteWhere(final Namer namer, final String table, final String name,
      final String term) {
    try {
      return Where.eq(table, name, Enum.valueOf(type, term));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  @Override
  public E[] newArray(final int size) {
    final E[] array = Arrays.copyOf(type.getEnumConstants(), size);
    Arrays.fill(array, null);
    return array;
  }

  @Override
  public E read(final String name, final ResultSet r, final DbVendor vendor)
      throws SQLException {
    return vendor.getEnum(r, type, name);
  }

  @Override
  public E read(final Json json, final E previousValue) {
    final String name = json.toString();
    return isEmpty(name) ? null : Enum.valueOf(type, name);
  }

  @Override
  public E read(final DataInput in)
      throws IOException {
    return type.getEnumConstants()[in.readInt()];
  }

  @Override
  public void write(final DataOutput out, final E e)
      throws IOException {
    out.writeInt(e.ordinal());
  }

  @Override
  public SqlType getType(final DbVendor vendor) {
    return vendor.getEnumType(type);
  }

  @Override
  public void bind(final PreparedStatement s, final DbVendor vendor, final Integer index, final E e)
      throws SQLException {
    vendor.setParameter(s, index, e);
  }
}
