package net.inetalliance.potion.cache;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

public class MemoryCache<V>
    extends ObjectCache<V> {

  private final Map<String, V> cache;

  public MemoryCache(final String id) {
    super(id);
    cache = new TreeMap<>();
  }

  @Override
  protected void cacheAdd(final String key, final V value) {

    cache.put(key, value);
  }

  @Override
  protected void cacheUpdate(final String key, final V value, final int time, final TimeUnit unit) {
    cache.put(key, value);
  }

  @Override
  protected void cacheUpdate(final String key, final V value) {
    cache.put(key, value);
  }

  @Override
  protected V cacheGet(final String key) {
    return cache.get(key);
  }

  @Override
  protected void cacheRemove(final String key) {
    cache.remove(key);
  }

  @Override
  protected void cacheAdd(final String key, final V value, final int time, final TimeUnit unit) {
    cache.put(key, value);
  }
}
