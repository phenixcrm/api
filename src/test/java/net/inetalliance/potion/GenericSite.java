package net.inetalliance.potion;

import net.inetalliance.potion.annotations.*;
import net.inetalliance.potion.query.Query;
import net.inetalliance.types.Currency;
import net.inetalliance.types.annotations.*;
import net.inetalliance.types.localized.LocalizedString;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

import static net.inetalliance.potion.annotations.Searchable.Weight.*;

@Persistent
@Versioned
public class GenericSite
    implements AutoCloseable {
  static {
    System.err.printf("static init of Generic Site%n");
  }

  @SubObject
  GenericRating rating;
  @SubObject
  DescribedRating described;
  @Required
  @PrimaryKey
  @Generated
  @Serial
  private Integer key;
  @Required
  @MaxLength(256)
  @Indexed
  @Matches("[A-Za-z -]*")
  @Searchable(A)
  private String name;
  @Required
  @MaxLength(1024)
  @Searchable(C)
  private URI uri;
  private Industry industry;
  private Locale locale;
  @GreaterThanOrEqual(0)
  private Currency handlingFee;
  private LocalizedString description;
  @Searchable(B)
  private String shortDescription;
  @Searchable(C)
  private String longDescription;

  public GenericSite() {
    this(null);
  }

  public GenericSite(final Integer key) {
    this.key = key;
  }

  @SuppressWarnings("SameParameterValue")
  static Query<GenericSite> withName(final String name) {
    return Query.eq(GenericSite.class, "name", name);
  }

  @Override
  public void close() {
    Locator.delete("junit", this);
  }

  void setShortDescription(final String shortDescription) {
    this.shortDescription = shortDescription;
  }

  Integer getKey() {
    return key;
  }

  void setKey(final Integer key) {
    this.key = key;
  }

  String getName() {
    return name;
  }

  void setName(final String name) {
    this.name = name;
  }

  public URI getUri() {
    return uri;
  }

  void setUri(final URI uri) {
    this.uri = uri;
  }

  Industry getIndustry() {
    return industry;
  }

  @SuppressWarnings("SameParameterValue")
  void setIndustry(final Industry industry) {
    this.industry = industry;
  }

  Locale getLocale() {
    return locale;
  }

  @SuppressWarnings("SameParameterValue")
  void setLocale(final Locale locale) {
    this.locale = locale;
  }

  Currency getHandlingFee() {
    return handlingFee;
  }

  void setHandlingFee(final Currency handlingFee) {
    this.handlingFee = handlingFee;
  }

  LocalizedString getDescription() {
    return description;
  }

  void setDescription(final LocalizedString description) {
    this.description = description;
  }

  public GenericRating getRating() {
    return rating;
  }

  void setRating(final GenericRating rating) {
    this.rating = rating;
  }

  public String getLongDescription() {
    return longDescription;
  }

  public void setLongDescription(final String longDescription) {
    this.longDescription = longDescription;
  }

  public DescribedRating getDescribed() {
    return described;
  }

  void setDescribed(final DescribedRating described) {
    this.described = described;
  }

  @Override
  public int hashCode() {
    int result = key != null ? key.hashCode() : 0;
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + (uri != null ? uri.hashCode() : 0);
    result = 31 * result + (industry != null ? industry.hashCode() : 0);
    result = 31 * result + (locale != null ? locale.hashCode() : 0);
    result = 31 * result + (handlingFee != null ? handlingFee.hashCode() : 0);
    result = 31 * result + (description != null ? description.hashCode() : 0);
    result = 31 * result + (shortDescription != null ? shortDescription.hashCode() : 0);
    result = 31 * result + (longDescription != null ? longDescription.hashCode() : 0);
    result = 31 * result + (rating != null ? rating.hashCode() : 0);
    result = 31 * result + (described != null ? described.hashCode() : 0);
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

    final GenericSite site = (GenericSite) o;

    if (!Objects.equals(described, site.described)) {
      return false;
    }
    if (!Objects.equals(description, site.description)) {
      return false;
    }
    if (!Objects.equals(handlingFee, site.handlingFee)) {
      return false;
    }
    if (!Objects.equals(key, site.key)) {
      return false;
    }
    if (!Objects.equals(locale, site.locale)) {
      return false;
    }
    if (!Objects.equals(longDescription, site.longDescription)) {
      return false;
    }
    if (!Objects.equals(name, site.name)) {
      return false;
    }
    if (!Objects.equals(rating, site.rating)) {
      return false;
    }
    if (!Objects.equals(shortDescription, site.shortDescription)) {
      return false;
    }
    if (industry != site.industry) {
      return false;
    }
    return Objects.equals(uri, site.uri);
  }

  public enum Industry {
    MEDICAL,
    ADULT
  }
}

