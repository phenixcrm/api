package net.inetalliance.potion.query;

import java.util.function.Function;

public class SortedQuery<T>
    extends DelegatingQuery<T> {

  public SortedQuery(final Query<T> delegate, final Function<String, String> src) {
    super(delegate, src, Function.identity(), t -> t);
  }

  public static <T> SortedQuery<T> all(final Class<T> type) {
    return new SortedQuery<>(Query.all(type), s -> s + "+");
  }

  public static <T> SortedQuery<T> none(final Class<T> type) {
    return new SortedQuery<>(Query.none(type), s -> s + "-");
  }

  @Override
  public SortedQuery<T> and(final Query<? super T> q) {
    return new SortedQuery<>(super.and(q), s -> q.getQuerySource() + "&&" + s);
  }

  @Override
  public SortedQuery<T> negate() {
    return new SortedQuery<>(super.negate(), s -> "-" + s);
  }

  @Override
  public SortedQuery<T> limit(final int limit) {
    return new SortedQuery<>(super.limit(limit), s -> s + "[" + limit + "]");
  }

  @Override
  public SortedQuery<T> limit(final Integer offset, final int limit) {
    return new SortedQuery<>(super.limit(offset, limit), s -> s + "[" + offset + "," + limit + "]");
  }

  @Override
  public SortedQuery<T> or(final Query<? super T> q) {
    return new SortedQuery<>(super.or(q), s -> q.getQuerySource() + "||" + s);
  }
}
