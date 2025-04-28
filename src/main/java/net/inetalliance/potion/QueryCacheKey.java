package net.inetalliance.potion;

import java.util.Objects;
import net.inetalliance.potion.jdbc.Parameters;
import net.inetalliance.potion.query.Query;

public class QueryCacheKey {

  public final Class type;
  private final String source;
  private final Parameters parameters;
  private final String additionalKeys;

  public QueryCacheKey(final Query query, final Parameters parameters,
      final String... additionalKeys) {
    type = query.type;
    source = query.getQuerySource();
    this.parameters = parameters;
    if (additionalKeys == null || additionalKeys.length == 0) {
      this.additionalKeys = null;
    } else {
      this.additionalKeys = String.join(":", additionalKeys);
    }
  }

  @Override
  public int hashCode() {
    int result = type.hashCode();
    result = 31 * result + source.hashCode();
    result = 31 * result + parameters.hashCode();
    result = 31 * result + (additionalKeys != null ? additionalKeys.hashCode() : 0);
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

    final QueryCacheKey key = (QueryCacheKey) o;

    return Objects.equals(additionalKeys, key.additionalKeys) && parameters.equals(key.parameters)
        && source.equals(
        key.source) && type.equals(key.type);

  }
}
