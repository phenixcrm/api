package net.inetalliance.potion;

import lombok.Getter;
import lombok.Setter;
import net.inetalliance.potion.annotations.Persistent;
import net.inetalliance.potion.annotations.PrimaryKey;

@Setter
@Getter
@Persistent
public class SimpleObject {

  @PrimaryKey
  public Integer key;
  private String name;

}
