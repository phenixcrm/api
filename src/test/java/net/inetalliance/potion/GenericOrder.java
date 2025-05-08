package net.inetalliance.potion;

import lombok.Getter;
import lombok.Setter;
import net.inetalliance.potion.annotations.ForeignKey;
import net.inetalliance.potion.annotations.Generated;
import net.inetalliance.potion.annotations.Indexed;
import net.inetalliance.potion.annotations.Persistent;
import net.inetalliance.potion.annotations.PrimaryKey;
import net.inetalliance.potion.annotations.Serial;

@Setter
@Getter
@Persistent
public class GenericOrder
    implements AutoCloseable {

  @PrimaryKey
  @ForeignKey
  protected GenericSite site;
  @PrimaryKey
  @Generated
  @Serial
  protected Integer key;
  @ForeignKey()
  @Indexed
  protected GenericProduct product;
  // this is here to test escaping
  protected String order;

  public GenericOrder() {
  }

  public GenericOrder(final GenericSite site) {
    this.site = site;
  }

  public GenericOrder(final GenericSite site, final Integer key) {
    this.site = site;
    this.key = key;
  }

  @Override
  public void close()
      throws Exception {
    Locator.delete("junit", this);
  }

    @Override
  public int hashCode() {
    int result = site != null ? site.hashCode() : 0;
    result = 31 * result + (key != null ? key.hashCode() : 0);
    result = 31 * result + (product != null ? product.hashCode() : 0);
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

    final GenericOrder order = (GenericOrder) o;

    return !(key != null ? !key.equals(order.key) : order.key != null) && !(product != null
        ? !product.equals(
        order.product) : order.product != null) && !(site != null ? !site.equals(order.site)
        : order.site != null);
  }
}

