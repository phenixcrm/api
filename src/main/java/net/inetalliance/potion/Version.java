package net.inetalliance.potion;

import net.inetalliance.potion.annotations.Generated;
import net.inetalliance.potion.annotations.Persistent;
import net.inetalliance.potion.annotations.PrimaryKey;
import net.inetalliance.potion.annotations.Serial;
import net.inetalliance.types.annotations.Required;

import java.time.LocalDateTime;

@Persistent
public class Version {

  @Generated
  @PrimaryKey
  @Serial
  public Integer version;
  @Required
  public String author;
  @Required
  public LocalDateTime modified;
  @Required
  public Boolean delete;
  @Required
  public Boolean update;

  public transient String modifiedFields;

  public Version() {
    super();
  }

  public Version(final String author, final LocalDateTime modified, final boolean delete,
                 final boolean update) {
    this.author = author;
    this.modified = modified;
    this.delete = delete;
    this.update = update;
  }
}
