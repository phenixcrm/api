package com.ameriglide.phenix.model;

import net.inetalliance.funky.StringFun;
import net.inetalliance.potion.info.Info;
import net.inetalliance.types.json.JsonMap;

import java.util.Objects;
import java.util.function.Function;

public class Key<T> {

  public final Class<T> type;
  public final String id;
  public final Info<T> info;
  public final Function<T, JsonMap> json;

  /**
   * Use Key.$(type, id)
   */
  protected Key(final Class<T> type, final String id) {
    this.type = type;
    this.id = id;
    info = Info.$(type);
    json = info::toJson;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Key<?> key = (Key<?>) o;
    return Objects.equals(type, key.type) && Objects.equals(id, key.id) && Objects.equals(info, key.info)
      && Objects.equals(json, key.json);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, id, info, json);
  }

  public static <T> Key<T> $(final Class<T> type, final String key) {
    return new Key<>(type, key);
  }

  @Override
  public String toString() {
    return StringFun.isEmpty(id) ? type.getSimpleName()
        : String.format("%s/%s", type.getSimpleName(), id);
  }
}
