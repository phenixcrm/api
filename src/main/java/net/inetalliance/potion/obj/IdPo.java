package net.inetalliance.potion.obj;

import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.inetalliance.potion.annotations.Generated;
import net.inetalliance.potion.annotations.Persistent;
import net.inetalliance.potion.annotations.PrimaryKey;
import net.inetalliance.potion.annotations.Serial;
import net.inetalliance.potion.annotations.Transient;
import net.inetalliance.potion.query.Query;
import net.inetalliance.sql.InCollectionWhere;

@Persistent
public class IdPo {

  @PrimaryKey
  @Generated
  @Serial
  public Integer id;

  protected IdPo() {
  }

  protected IdPo(final Integer id) {
    this.id = id;
  }

  public static <I extends IdPo> Query<I> withId(final Class<I> type, final Integer id) {
    return Query.eq(type, "id", id);
  }
  public static <I extends IdPo> Query<I> withIdIn(final Class<I> type, final Set<Integer> ids) {
    return Query.in(type, "id", ids);
  }

  public static <I extends IdPo> Query<I> idIn(final Class<I> type,
      final Collection<Integer> collection) {
    return new Query<>(type, t -> collection.contains(t.id),
        (namer, table) -> new InCollectionWhere(table, "id", collection));
  }

  public static <I extends IdPo> Query<I> notIn(final Class<I> type,
      final Collection<I> collection) {
    if (collection.isEmpty()) {
      return Query.all(type);
    }
    return new Query<>(type, t -> !collection.contains(t),
        (namer, table) -> new InCollectionWhere(table, "id",
            collection.stream()
                .map(
                    i -> i.id)
                .collect(
                    toList()))
            .negate());
  }

  public static List<Integer> mapId(final Collection<? extends IdPo> pos) {
    return pos.stream().map(p -> p.id).collect(toList());
  }

  @Override
  @Transient
  public int hashCode() {
    return id != null ? id.hashCode() : 0;
  }

  @Override
  @Transient
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final IdPo idPo = (IdPo) o;

    return Objects.equals(id, idPo.id);
  }
}
