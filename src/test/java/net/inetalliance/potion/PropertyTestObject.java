package net.inetalliance.potion;

import java.net.URI;
import java.util.Locale;
import net.inetalliance.potion.annotations.ForeignKey;
import net.inetalliance.potion.annotations.Generated;
import net.inetalliance.potion.annotations.Persistent;
import net.inetalliance.potion.annotations.PrimaryKey;
import net.inetalliance.potion.annotations.Serial;
import net.inetalliance.types.Currency;
import net.inetalliance.types.localized.LocalizedString;

@Persistent
public class PropertyTestObject {

  Integer integerValue;
  Currency currencyValue;
  Double doubleValue;
  TestEnum enumValue;
  Float floatValue;
  @Generated
  @PrimaryKey
  @Serial
  Integer idValue;
  Locale localeValue;
  LocalizedString localizedStringValue;
  Long longValue;
  URI objectValue;
  String stringValue;
  @ForeignKey
  PropertyTestForeignObject foreignValue;

  @Override
  public int hashCode() {
    int result = integerValue != null ? integerValue.hashCode() : 0;
    result = 31 * result + (currencyValue != null ? currencyValue.hashCode() : 0);
    result = 31 * result + (doubleValue != null ? doubleValue.hashCode() : 0);
    result = 31 * result + (enumValue != null ? enumValue.hashCode() : 0);
    result = 31 * result + (floatValue != null ? floatValue.hashCode() : 0);
    result = 31 * result + (idValue != null ? idValue.hashCode() : 0);
    result = 31 * result + (localeValue != null ? localeValue.hashCode() : 0);
    result = 31 * result + (localizedStringValue != null ? localizedStringValue.hashCode() : 0);
    result = 31 * result + (longValue != null ? longValue.hashCode() : 0);
    result = 31 * result + (objectValue != null ? objectValue.hashCode() : 0);
    result = 31 * result + (stringValue != null ? stringValue.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final PropertyTestObject object = (PropertyTestObject) o;

    if (currencyValue != null ? !currencyValue.equals(object.currencyValue)
        : object.currencyValue != null) {
      return false;
    }
    if (doubleValue != null ? !doubleValue.equals(object.doubleValue)
        : object.doubleValue != null) {
      return false;
    }
    if (enumValue != object.enumValue) {
      return false;
    }
    if (floatValue != null ? !floatValue.equals(object.floatValue) : object.floatValue != null) {
      return false;
    }
    if (idValue != null ? !idValue.equals(object.idValue) : object.idValue != null) {
      return false;
    }
    if (integerValue != null ? !integerValue.equals(object.integerValue)
        : object.integerValue != null) {
      return false;
    }
    if (localeValue != null ? !localeValue.equals(object.localeValue)
        : object.localeValue != null) {
      return false;
    }
    if (localizedStringValue != null
        ? !localizedStringValue.equals(object.localizedStringValue)
        : object.localizedStringValue != null) {
      return false;
    }
    if (longValue != null ? !longValue.equals(object.longValue) : object.longValue != null) {
      return false;
    }
    if (objectValue != null ? !objectValue.equals(object.objectValue)
        : object.objectValue != null) {
      return false;
    }
    return !(stringValue != null ? !stringValue.equals(object.stringValue)
        : object.stringValue != null);
  }

  public static enum TestEnum {
    BLAH,
    ARGH,
    STUFF
  }
}
