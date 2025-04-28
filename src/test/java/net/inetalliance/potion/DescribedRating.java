package net.inetalliance.potion;

import net.inetalliance.potion.annotations.Persistent;
import net.inetalliance.types.annotations.SubObject;

@Persistent(locatable = false)
public class DescribedRating {

  @SubObject
  public GenericRating rating;
  @SubObject
  public GenericRating alternate;
  public String description;
}
