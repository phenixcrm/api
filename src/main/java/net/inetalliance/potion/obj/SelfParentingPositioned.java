package net.inetalliance.potion.obj;

import static net.inetalliance.potion.Locator.$$;

import java.util.SortedSet;
import net.inetalliance.sql.OrderBy;

public interface SelfParentingPositioned<T extends SelfParenting<T>>
    extends SelfParenting<T>, Positioned {

  default SortedSet<T> getChildren() {
    return $$(SelfParenting.Q.children(this).orderBy(property, OrderBy.Direction.ASCENDING, true));
  }

}