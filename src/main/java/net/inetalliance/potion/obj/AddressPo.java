package net.inetalliance.potion.obj;

import net.inetalliance.potion.annotations.Persistent;
import net.inetalliance.potion.annotations.Searchable;
import net.inetalliance.types.annotations.MaxLength;
import net.inetalliance.types.annotations.PhoneNumber;
import net.inetalliance.types.geopolitical.Country;
import net.inetalliance.types.geopolitical.canada.Province;
import net.inetalliance.types.geopolitical.us.State;

import static net.inetalliance.potion.annotations.Searchable.Weight.B;

@Persistent
public abstract class AddressPo {

  private static final String longPhone = "(%s) %s-%s";
  private static final String longPhoneWithOne = "1 (%s) %s-%s";
  private static final String shortPhone = "%s-%s";
  protected Province canadaDivision;
  @MaxLength(128)
  @Searchable(B)
  protected String city;
  @Searchable
  protected String name;
  @Searchable
  protected State state;
  @MaxLength(128)
  @Searchable(B)
  protected String street;
  @MaxLength(128)
  protected String street2;
  private Country country;
  @MaxLength(64)
  private String county;
  @MaxLength(64)
  @PhoneNumber
  @Searchable
  private String fax;
  @PhoneNumber
  @MaxLength(64)
  @Searchable
  private String phone;
  @PhoneNumber
  @MaxLength(64)
  @Searchable
  private String phone2;
  @MaxLength(64)
  private String postalCode;

  protected AddressPo() {
    country = Country.UNITED_STATES;
  }

  public static String formatPhoneNumber(final String raw) {
    if (raw == null) {
      return "";
    }
    final int len = raw.length();
    return switch (len) {
      case 7 -> String.format(shortPhone, raw.substring(0, 3), raw.substring(3));
      case 10 -> String.format(longPhone, raw.substring(0, 3), raw.substring(3, 6), raw.substring(6));
      case 11 -> String.format(longPhoneWithOne, raw.substring(1, 4), raw.substring(4, 7), raw.substring(7));
      default -> raw;
    };
  }

  public static String unformatPhoneNumber(final String phone) {
    if (phone == null) {
      return null;
    }
    final int l = phone.length();
    final StringBuilder s = new StringBuilder(phone.length());
    for (int i = 0; i < l; i++) {
      final char c = phone.charAt(i);
      if (Character.isLetterOrDigit(c)) {
        s.append(c);
      }
    }
    return s.toString();
  }

  public Province getCanadaDivision() {
    return canadaDivision;
  }

  public AddressPo setCanadaDivision(final Province canadaDivision) {
    this.canadaDivision = canadaDivision;
    return this;
  }

  public String getCity() {
    return city;
  }

  public AddressPo setCity(final String city) {
    this.city = city;
    return this;
  }

  public Country getCountry() {
    return country;
  }

  public AddressPo setCountry(final Country country) {
    this.country = country;
    return this;
  }

  public String getCounty() {
    return county;
  }

  public AddressPo setCounty(final String county) {
    this.county = county;
    return this;
  }

  public String getFax() {
    return fax;
  }

  public AddressPo setFax(final String fax) {
    this.fax = fax;
    return this;
  }

  public String getName() {
    return name;
  }

  public AddressPo setName(final String name) {
    this.name = name;
    return this;
  }

  public String getPhone() {
    return phone;
  }

  public AddressPo setPhone(final String phone) {
    this.phone = phone;
    return this;
  }

  public String getPhone2() {
    return phone2;
  }

  public AddressPo setPhone2(String phone2) {
    this.phone2 = phone2;
    return this;
  }

  public String getPostalCode() {
    return postalCode;
  }

  public AddressPo setPostalCode(final String postalCode) {
    this.postalCode = postalCode;
    return this;
  }

  public State getState() {
    return state;
  }

  public AddressPo setState(final State state) {
    this.state = state;
    return this;
  }

  public String getStreet() {
    return street;
  }

  public AddressPo setStreet(final String street) {
    this.street = street;
    return this;
  }

  public String getStreet2() {
    return street2;
  }

  public AddressPo setStreet2(String street2) {
    this.street2 = street2;
    return this;
  }

  @Override
  public String toString() {
    if (country == null || state == null) {
      return "(None)";
    }
    final StringBuilder buffer = new StringBuilder(0);
    if (country == Country.UNITED_STATES) {
      buffer.append(street);
      buffer.append(", ");
      buffer.append(street2);
      buffer.append(", ");
      buffer.append(city);
      buffer.append(", ");
      buffer.append(state.getAbbreviation());
      buffer.append(' ');
      buffer.append(postalCode);
      if (county != null) {
        buffer.append(", ");
        buffer.append(county);
      }
      buffer.append(", ");
      buffer.append(country.getLocalizedName());
      buffer.append(", Phone: ");
      buffer.append(phone);
      buffer.append(", Phone #2: ");
      buffer.append(phone2);
      if (fax != null && fax.trim().length() > 0) {
        buffer.append(", Fax: ");
        buffer.append(fax);
      }
    } else {
      buffer.append(street);
      buffer.append(", ");
      buffer.append(street2);
      buffer.append(", ");
      buffer.append(city);
      buffer.append(", ");
      buffer.append(postalCode);
      if (county != null) {
        buffer.append(", ");
        buffer.append(county);
      }
      buffer.append(", ");
      buffer.append(country.getLocalizedName());
      buffer.append(", Phone: ");
      buffer.append(phone);
      buffer.append(", Phone #2: ");
      buffer.append(phone2);
      if (fax != null && fax.trim().length() > 0) {
        buffer.append(", Fax: ");
        buffer.append(fax);
      }
    }
    return buffer.toString();
  }
}
