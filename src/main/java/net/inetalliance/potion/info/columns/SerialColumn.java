package net.inetalliance.potion.info.columns;

import net.inetalliance.sql.DbVendor;
import net.inetalliance.sql.SqlType;

public class SerialColumn
    extends IntegerColumn {

  public SerialColumn(final String name, final boolean required, final boolean unique) {
    super(name, required, unique);
  }

  @Override
  public SqlType getType(final DbVendor vendor) {
    return vendor.getSerialType();
  }
}
