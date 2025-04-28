package net.inetalliance.potion;

import java.util.Collection;
import java.util.SortedSet;
import net.inetalliance.potion.annotations.ForeignKey;
import net.inetalliance.potion.annotations.Indexed;
import net.inetalliance.potion.annotations.ManyToMany;
import net.inetalliance.potion.annotations.Persistent;
import net.inetalliance.potion.annotations.PrimaryKey;
import net.inetalliance.potion.annotations.Searchable;
import net.inetalliance.potion.obj.IdPo;
import net.inetalliance.types.annotations.Required;

@Persistent
public class GenericProduct
    extends IdPo
    implements AutoCloseable {

  @Required
  @Searchable
  protected String name;
  @PrimaryKey
  @ForeignKey
  @Indexed
  private GenericSite site;
  @ManyToMany(GenericCategory.class)
  private Collection<GenericCategory> tags;
  @ManyToMany(GenericCategory.class)
  private SortedSet<GenericCategory> sortedTags;

  @SuppressWarnings("unused")
  public GenericProduct() {
    super();
  }

  GenericProduct(final GenericSite site) {
    this(site, null);
  }

  GenericProduct(final GenericSite site, final Integer id) {
    super(id);
    this.site = site;
  }

  @Override
  public void close() {
    Locator.delete("junit", this);
  }

  @Override
  public String toString() {
    return String.format("GenericProduct(%s)", id);
  }

  SortedSet<GenericCategory> getSortedTags() {
    return sortedTags;
  }

  GenericSite getSite() {
    return site;
  }

  void setSite(final GenericSite site) {
    this.site = site;
  }

  Collection<GenericCategory> getTags() {
    return tags;
  }

  String getName() {
    return name;
  }

  void setName(final String name) {
    this.name = name;
  }
}


