package net.inetalliance.potion;

import net.inetalliance.potion.annotations.Persistent;
import net.inetalliance.potion.obj.IdPo;
import net.inetalliance.types.annotations.MaxLength;
import net.inetalliance.types.annotations.Required;

@Persistent
public class GenericCategory
    extends IdPo
    implements AutoCloseable {

  @Required
  @MaxLength(128)
  protected String name;

  public GenericCategory() {
    this(null, null);
  }

  public GenericCategory(final Integer id, final String name) {
    super(id);
    this.name = name;
  }

  public GenericCategory(final String name) {
    this(null, name);
  }

  @Override
  public void close()
      throws Exception {
    Locator.delete("junit", this);
  }

  @Override
  public String toString() {
    return String.format("GenericCategory(%s)", id);
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }
}
