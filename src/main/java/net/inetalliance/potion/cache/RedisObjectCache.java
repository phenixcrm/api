package net.inetalliance.potion.cache;

import com.ameriglide.phenix.core.Log;
import com.ameriglide.phenix.core.Redis;
import net.inetalliance.potion.info.Info;
import net.inetalliance.types.json.JsonMap;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static net.inetalliance.types.json.Json.ugly;

public class RedisObjectCache<V> extends ObjectCache<V> {

  private static final Log log = new Log();

  private static final RedissonClient redis = Redis.getClient();

  private final RMapCache<String, String> rMap;
  private final Info<V> info;

  private RedisObjectCache(final String id, final Class<V> type) {
    super(id);
    this.info = Info.$(type);
    rMap = redis.getMapCache(id);
  }

  public static <V> ObjectCache<V> $(final String ns, final String name, final Class<V> type) {
    final String id = String.format("%s-%s", ns, name);
    log.info(() -> "RedisObjectCache %s".formatted(id));
    return new RedisObjectCache<>(id, type);
  }


  public void clear() {
    rMap.clear();
  }

  @Override
  public void cacheAdd(final String key, final V value) {
    rMap.putIfAbsentAsync(key, ugly(info.toJson(value)), 15, MINUTES);
  }

  @Override
  public void cacheUpdate(final String key, final V value, final int time, final TimeUnit unit) {
    rMap.putAsync(key, ugly((info.toJson(value))), 15, MINUTES);
  }

  @Override
  public void cacheUpdate(final String key, final V value) {
    cacheAdd(key, value);
  }

  public V cacheGet(final String key) {
    try {
      var json = rMap.getAsync(key).get(100, MILLISECONDS);

      if (json==null) {
        return null;
      } else {
        final V v;
        try {
          v = info.type.getDeclaredConstructor().newInstance();
        } catch (InstantiationException |
                 IllegalAccessException |
                 NoSuchMethodException |
                 InvocationTargetException e) {
          throw new RuntimeException(e);
        }
        info.fromJson(v, JsonMap.parse((String)json));
        return v;
      }

    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      log.debug(() -> "redis get unsuccessful", e);
      return null;
    }

  }

  @Override
  public void cacheRemove(final String key) {
    rMap.removeAsync(key);
  }

  @Override
  public void cacheAdd(final String key, final V value, final int time, final TimeUnit unit) {
    cacheUpdate(key, value, time, unit);
  }
}
