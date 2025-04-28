package net.inetalliance.potion.cache;

import com.ameriglide.phenix.core.Log;
import net.inetalliance.potion.Hash;
import net.inetalliance.potion.Shell;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static net.inetalliance.potion.Locator.getDbName;

public abstract class ObjectCache<V> {

  private static final Log log = new Log();
  private final String id;

  protected ObjectCache(final String id) {
    super();
    this.id = id;
  }

  @SuppressWarnings("unchecked")
  public static <V> ObjectCache<V> nullCache() {
    return NullObjectCache.$;
  }

  public static <V> ObjectCache<V> $(final String ns, final String name, final Class<V> type) {
    return LruObjectCache.$(ns, name);
  }

  public void update(final String key, final V value, final int time, final TimeUnit unit) {
    final String prefix = prefix(key);
    log.trace(() -> "set %s->%s: %s expire: %d %s".formatted(key, prefix, value, time, unit));
    cacheUpdate(prefix, value, time, unit);
  }

  protected String prefix(final String key) {
    return format("%s:%s:%s", getDbName(), id, key);
  }

  protected abstract void cacheUpdate(String key, V value, int time, TimeUnit unit);

  public void add(final String key, final V value, final int time, final TimeUnit unit) {
    final String prefix = prefix(key);
    log.trace(() -> "add %s->%s: %d %s".formatted(key, prefix, value, time, unit));
    cacheAdd(prefix, value, time, unit);
  }

  protected abstract void cacheAdd(String key, V value, int time, TimeUnit unit);

  public void add(final Hash hash, final V value) {
    add(hash.toString(), value);
  }

  public void add(final String key, final V value) {
    final String prefix = prefix(key);
    log.trace(() -> "add %s->%s: %s".formatted(key, prefix, value));
    cacheAdd(prefix, value);
  }

  protected abstract void cacheAdd(String key, V value);

  @SuppressWarnings({"unchecked"})
  public <O> O get(final Hash<O> hash) {
    return (O) get(hash.toString());
  }

  public V get(final String key) {
    final String prefix = prefix(key);
    log.trace(() -> "get %s->%s".formatted(key, prefix));
    final Object o = cacheGet(prefix);
    return o==null ? null:Shell.decode(o);
  }

  protected abstract V cacheGet(String key);

  public void remove(final Hash hash) {
    remove(hash.toString());
  }

  public void remove(final String key) {
    final String prefix = prefix(key);
    log.trace(() -> "del %s->%s".formatted(key, prefix));
    cacheRemove(prefix);
  }

  protected abstract void cacheRemove(String key);

  public void addAll(final Map<String, V> map) {
    for (final Map.Entry<String, V> entry : map.entrySet()) {
      add(entry.getKey(), entry.getValue());
    }
  }

  public void updateAll(final Map<String, V> map) {
    for (final Map.Entry<String, V> entry : map.entrySet()) {
      update(entry.getKey(), entry.getValue());
    }
  }

  public void update(final String key, final V value) {
    final String prefix = prefix(key);
    log.trace(() -> "set %s->%s: %s".formatted(key, prefix, value));
    cacheUpdate(prefix, value);
  }

  protected abstract void cacheUpdate(String key, V value);

  public void update(final Hash hash, final V value) {
    update(hash.toString(), value);
  }

}
