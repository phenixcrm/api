package net.inetalliance.potion;

import net.inetalliance.potion.annotations.Persistent;

@Persistent(locatable = false)
public class GenericRating {

  public Integer stars;
  public String review;

  @Override
  public int hashCode() {
    int result = stars != null ? stars.hashCode() : 0;
    result = 31 * result + (review != null ? review.hashCode() : 0);
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

    final GenericRating rating = (GenericRating) o;

    return !(review != null ? !review.equals(rating.review) : rating.review != null) && !(
        stars != null ?
            !stars.equals(
                rating.stars) : rating.stars != null);
  }
}
