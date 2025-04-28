package net.inetalliance.potion;

import static net.inetalliance.potion.annotations.ForeignKeyChange.CASCADE;

import java.util.Objects;
import net.inetalliance.potion.annotations.ForeignKey;
import net.inetalliance.potion.annotations.Generated;
import net.inetalliance.potion.annotations.Persistent;
import net.inetalliance.potion.annotations.PrimaryKey;
import net.inetalliance.potion.annotations.Serial;
import net.inetalliance.types.annotations.Required;

@Persistent
public class GenericSale
    implements AutoCloseable {

  @PrimaryKey
  @ForeignKey
  private GenericSite site;
  @PrimaryKey
  @Generated
  @Serial
  private Integer id;
  @ForeignKey(onDelete = CASCADE)
  @Required
  private GenericProduct product;

  @SuppressWarnings("unused")
  public GenericSale() {
  }

  GenericSale(final GenericSite site) {
    this.site = site;
  }

  GenericSale(final GenericSite site, final Integer id) {
    this.site = site;
    this.id = id;
  }

  @Override
  public void close() {
    Locator.delete("junit", this);
  }

  Integer getId() {
    return id;
  }

  void setProduct(final GenericProduct product) {
    this.product = product;
  }

  @Override
  public int hashCode() {
    int result = site != null ? site.hashCode() : 0;
    result = 31 * result + (id != null ? id.hashCode() : 0);
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

    final GenericSale sale = (GenericSale) o;

    if (!Objects.equals(id, sale.id)) {
      return false;
    }
    if (!Objects.equals(product, sale.product)) {
      return false;
    }
    return Objects.equals(site, sale.site);

  }
}
