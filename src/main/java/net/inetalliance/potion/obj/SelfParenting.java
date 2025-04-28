package net.inetalliance.potion.obj;

import com.ameriglide.phenix.core.Iterators;
import net.inetalliance.potion.FunctorBreadthFirstIterator;
import net.inetalliance.potion.info.Info;
import net.inetalliance.potion.query.Query;
import net.inetalliance.types.Named;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public interface SelfParenting<T extends SelfParenting<T>>
    extends Named {

  default long getDepth() {
    return ancestors().count();
  }

  @SuppressWarnings("unchecked")
  default Stream<T> ancestors() {
    return Iterators.stream(new Iterator<T>() {
      private T nextChild = (T) SelfParenting.this;

      @Override
      public boolean hasNext() {
        return nextChild.getParent() != null;
      }

      @Override
      public T next() {
        nextChild = nextChild.getParent();
        return nextChild;
      }
    });
  }

  T getParent();

  default boolean isDescendantOf(T potentialAncestor){
    return potentialAncestor.ancestors().anyMatch(team -> Objects.equals(team,potentialAncestor.getParent()));
  };

  @SuppressWarnings("unchecked")
  default Iterator<T> toBreadthFirstIterator() {
    return new FunctorBreadthFirstIterator<>((T) this, t -> t.getChildren().iterator());
  }

  Iterable<T> getChildren();

  default List<T> breadcrumbs() {
    final List<T> breadcrumbs = new ArrayList<>(8);
    ancestors().forEach(a -> breadcrumbs.add(0, a));
    return breadcrumbs;
  }

  class Q {

    @SuppressWarnings("unchecked")
    static <T extends SelfParenting<T>> Query<T> children(final SelfParenting<T> parent) {
      return Query.eq((Class<T>) Info.$(parent).type, "parent", parent);
    }

  }

}
