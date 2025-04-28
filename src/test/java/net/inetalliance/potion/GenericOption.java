package net.inetalliance.potion;

import net.inetalliance.potion.annotations.*;
import net.inetalliance.types.annotations.MaxLength;
import net.inetalliance.types.annotations.Required;

@Persistent
public class GenericOption
    implements AutoCloseable {

  @Required
  @MaxLength(128)
  protected String name;
  @PrimaryKey
  @ForeignKey
  private GenericProduct product;
  @PrimaryKey
  @Generated
  @Serial
  private Integer key;

  @SuppressWarnings("unused")
  public GenericOption() {
    super();
  }

  GenericOption(final GenericProduct product) {
    this(product, null);
  }

  GenericOption(final GenericProduct product, final Integer key) {
    this.product = product;
    this.key = key;
  }

  @Override
  public void close() {
    Locator.delete("junit", this);
  }

  void setProduct(final GenericProduct product) {
    this.product = product;
  }

  Integer getKey() {
    return key;
  }

  String getName() {
    return name;
  }

  @SuppressWarnings("SameParameterValue")
  void setName(final String name) {
    this.name = name;
  }
}
