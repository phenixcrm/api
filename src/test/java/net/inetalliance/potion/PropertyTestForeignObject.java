package net.inetalliance.potion;

import net.inetalliance.potion.annotations.Persistent;
import net.inetalliance.potion.annotations.PrimaryKey;

@Persistent
public class PropertyTestForeignObject {

  @PrimaryKey
  Integer key;
  String name;

  public PropertyTestForeignObject() {
  }

  public PropertyTestForeignObject(final Integer key) {
    this.key = key;
  }
}
