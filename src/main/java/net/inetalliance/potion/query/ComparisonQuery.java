package net.inetalliance.potion.query;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import net.inetalliance.potion.info.Info;
import net.inetalliance.potion.info.Property;
import net.inetalliance.sql.Namer;
import net.inetalliance.sql.Where;

public final class ComparisonQuery<T, V extends Comparable<V>>
    extends Query<T> {

  protected Property<T, V> property;

  public ComparisonQuery(final Class<T> type, final String srcOp, final String name, final V value,
      final BiPredicate<V, V> predicate, final BiFunction<Namer, String, Where> where) {
    this(type, type.getName() + "." + name + srcOp + value, value, Info.$(type).get(name),
        predicate, where);

  }

  private ComparisonQuery(final Class<T> type, final String src, final V value,
      final Property<T, V> property,
      final BiPredicate<V, V> predicate, final BiFunction<Namer, String, Where> where) {
    super(type, src, t -> predicate.test(property.apply(t), value), where);

  }

}

