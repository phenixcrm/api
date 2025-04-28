package net.inetalliance.potion.cache;

import java.util.concurrent.TimeUnit;

class NullObjectCache
    extends ObjectCache {

  public static final NullObjectCache $ = new NullObjectCache("object caching disabled");

  private NullObjectCache(final String id) {
    super(id);
  }

  @Override
  protected void cacheUpdate(final String key, final Object value, final int time,
      final TimeUnit unit) {

  }

  @Override
  protected void cacheAdd(final String key, final Object value, final int time,
      final TimeUnit unit) {

  }

  @Override
  protected void cacheAdd(final String key, final Object value) {

  }

  @Override
  protected Object cacheGet(final String key) {
    return null;
  }

  @Override
  protected void cacheRemove(final String key) {
  }

  @Override
  protected void cacheUpdate(final String key, final Object value) {

  }
}
