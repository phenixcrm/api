package net.inetalliance.potion.cache;

import com.ameriglide.phenix.core.Log;
import net.inetalliance.types.struct.caches.LruCache;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LruObjectCache<V>
    extends ObjectCache<V> {

  private static final Log log = new Log();
  private final Map<String, V> data;

  protected LruObjectCache(final String id) {
    super(id);
    data = new LruCache<>(100);
  }

  public static <V> ObjectCache<V> $(final String ns, final String name) {
    final String id = String.format("%s-%s", ns, name);
    log.info(()->"LRU Object Cache %s".formatted(id));
    return new LruObjectCache<>(id);
  }

  @Override
  protected void cacheUpdate(final String key, final V value, final int time, final TimeUnit unit) {
    data.put(key, value);

  }

  @Override
  protected void cacheAdd(final String key, final V value, final int time, final TimeUnit unit) {
    data.put(key, value);

  }

  @Override
  protected void cacheAdd(final String key, final V value) {
    data.put(key, value);

  }

  @Override
  protected V cacheGet(final String key) {
    return data.get(key);
  }

  @Override
  protected void cacheRemove(final String key) {
    data.remove(key);
  }

  @Override
  protected void cacheUpdate(final String key, final V value) {
    data.put(key,value);
  }
}
