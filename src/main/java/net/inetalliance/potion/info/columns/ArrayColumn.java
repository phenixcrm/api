package net.inetalliance.potion.info.columns;

import net.inetalliance.potion.info.Column;
import net.inetalliance.sql.DbVendor;
import net.inetalliance.sql.Namer;
import net.inetalliance.sql.SqlType;
import net.inetalliance.sql.Where;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonList;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ArrayColumn<T>
    extends Column<T[]> {

  private final Column<T> column;

  public ArrayColumn(final Column<T> column, final String name, final boolean required,
      final boolean unique) {
    super(name, required, unique);
    this.column = column;
  }

	@Override
  public Where getAutoCompleteWhere(final Namer namer, final String table, final String name,
      final String term) {
    throw new UnsupportedOperationException("Autocomplete is not supported for date array columns");
  }

  @Override
  public T[][] newArray(final int size) {
    throw new UnsupportedOperationException();
  }

  @SuppressWarnings({"unchecked"})
  @Override
  public T[] read(final String name, final ResultSet r, final DbVendor vendor)
      throws SQLException {
    final Array array = r.getArray(name);
    if (r.wasNull()) {
      return null;
    } else {
      return (T[]) array.getArray();
    }
  }

  @Override
  public T[] read(final Json json, final T[] previousValue) {
    final JsonList list = (JsonList) json;
    final T[] array = column.newArray(list.size());
    int i = 0;
    for (final Json item : list) {
      array[i] = column.read(item, previousValue[i]);
      i++;
    }
    return array;
  }

  @Override
  public T[] read(final DataInput in)
      throws IOException {
    final int size = in.readInt();
    final T[] array = column.newArray(size);
    for (int i = 0; i < size; i++) {
      array[i] = column.read(in);
    }
    return array;
  }

  @Override
  public void write(final DataOutput out, final T[] ts)
      throws IOException {
    out.writeInt(ts.length);
    for (final T t : ts) {
      column.write(out, t);
    }
  }

  @Override
  public SqlType getType(final DbVendor vendor) {
    return SqlType.sqlArray;
  }

  @Override
  public void bind(final PreparedStatement s, final DbVendor vendor, final Integer index,
      final T[] ts)
      throws SQLException {
    s.setArray(index, s.getConnection().createArrayOf(column.getType(vendor).name, ts));
  }
}
