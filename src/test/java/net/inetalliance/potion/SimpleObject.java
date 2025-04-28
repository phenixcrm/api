package net.inetalliance.potion;

import net.inetalliance.potion.annotations.Persistent;
import net.inetalliance.potion.annotations.PrimaryKey;

@Persistent
public class SimpleObject {

  @PrimaryKey
  public Integer key;
  private String name;

  public Integer getKey() {
    return key;
  }

  public void setKey(final Integer key) {
    this.key = key;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }
}
