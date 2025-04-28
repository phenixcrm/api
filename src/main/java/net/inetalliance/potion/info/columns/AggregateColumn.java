package net.inetalliance.potion.info.columns;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import net.inetalliance.potion.info.Column;
import net.inetalliance.potion.info.SingleColumnProperty;
import net.inetalliance.sql.Aggregate;
import net.inetalliance.sql.AggregateField;
import net.inetalliance.sql.DbVendor;
import net.inetalliance.sql.Namer;
import net.inetalliance.sql.SqlType;
import net.inetalliance.sql.Where;
import net.inetalliance.types.json.Json;

public class AggregateColumn<T>
    extends Column<T> {

  public final AggregateField field;
  private final Column<T> column;

  private AggregateColumn(final Aggregate function, final String name, final Column<T> column) {
    super(name, false, false);
    this.field = new AggregateField(function, name);
    this.column = column;
  }

  public static <T> AggregateColumn<T> $(final Aggregate function, final String name,
      final Class<T> type) {
    return new AggregateColumn<T>(function, name, SingleColumnProperty.$(type));
  }

	@Override
  public Where getAutoCompleteWhere(final Namer namer, final String table, final String name,
      final String term) {
    throw new UnsupportedOperationException("Autocomplete is not supported for aggregate columns");
  }

  @Override
  public T[] newArray(final int size) {
    return null;
  }

  @Override
  public T read(final String name, final ResultSet r, final DbVendor vendor)
      throws SQLException {
    return column.read(name, r, vendor);
  }

  @Override
  public T read(final Json json, final T previousValue) {
    return column.read(json, previousValue);
  }

  @Override
  public T read(final DataInput in)
      throws IOException {
    return column.read(in);
  }

  @Override
  public void write(final DataOutput out, final T t)
      throws IOException {
    column.write(out, t);
  }

  @Override
  public SqlType getType(final DbVendor vendor) {
    return column.getType(vendor);
  }

  @Override
  public void bind(final PreparedStatement s, final DbVendor vendor, final Integer index, final T t)
      throws SQLException {
    column.bind(s, vendor, index, t);
  }
}
