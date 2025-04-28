package net.inetalliance.potion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.SortedSet;
import net.inetalliance.potion.query.SortedQuery;

/**
 * It's a set of heterogeneous limit query results, where the next item is chosen with a comparator
 * from each list, and if a list gets emptied, it loads with the next items.
 */
public class RevolvingDispenser<T>
    implements Iterator<T> {

  private final int limit;
  private final int total;
  private final Comparator<T> comparator;
  private final List<Bucket> buckets;

  private RevolvingDispenser(final int limit, final Comparator<T> comparator,
      final Collection<SortedQuery<? extends T>> queries) {
    this.limit = limit;
    this.comparator = comparator;
    buckets = new ArrayList<>(queries.size());
    for (final SortedQuery<? extends T> query : queries) {
      final Bucket bucket = new Bucket(query);
      if (!bucket.isEmpty()) {
        buckets.add(bucket);
      }
    }
    total = queries.stream().map(Locator::count).reduce(Integer::sum).orElse(0);

  }

  public static <T> RevolvingDispenser<T> $(final int limit, final Comparator<T> comparator,
      final Collection<SortedQuery<? extends T>> queries) {
    return new RevolvingDispenser<>(limit, comparator, queries);
  }

  public static <T> RevolvingDispenser<T> $(final int limit, final Comparator<T> comparator,
      final SortedQuery<? extends T>... queries) {
    return new RevolvingDispenser<>(limit, comparator, Arrays.asList(queries));
  }

  public int getTotal() {
    return total;
  }

  // --------------------- Interface Iterator ---------------------
  @Override
  public boolean hasNext() {
    return !buckets.isEmpty();
  }

  @Override
  public T next() {
    if (buckets.isEmpty()) {
      throw new NoSuchElementException();
    }
    final Bucket bucket = Collections.min(buckets);
    final T value = bucket.get();
    if (bucket.isEmpty()) {
      buckets.remove(bucket);
    }
    return value;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }

  private class Bucket
      implements Comparable<Bucket> {

    private final SortedQuery<? extends T> query;
    private SortedSet<? extends T> data;
    private Integer offset;

    private Bucket(final SortedQuery<? extends T> query) {
      this.query = query;
      loadMore();
    }

    private void loadMore() {
      data = Locator.$$(query.limit(offset, limit));
      offset = offset == null ? limit : offset + limit;
    }

    @Override
    public int hashCode() {
      return query.type.hashCode();
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final Bucket bucket = (Bucket) o;
      return query.type.equals(bucket.query.type);
    }

    public boolean isEmpty() {
      return data.isEmpty();
    }

    @Override
    public int compareTo(final Bucket that) {
      return comparator.compare(this.data.first(), that.data.first());
    }

    public T get() {
      final T t = data.first();
      data.remove(t);
      if (data.isEmpty()) {
        loadMore();
      }
      return t;
    }
  }
}
