package net.inetalliance.potion.cache;

import com.ameriglide.phenix.core.Log;
import com.ameriglide.phenix.core.Redis;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonList;
import net.inetalliance.types.json.JsonMap;
import org.redisson.api.RMapCache;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.ameriglide.phenix.core.Strings.isEmpty;
import static net.inetalliance.types.json.Json.ugly;

public class RedisJsonCache {

  private static final Log log = new Log();

  static {

  }

  private final RMapCache<String, String> rMap;

  public RedisJsonCache(final String name) {
    this.rMap = Redis.getClient().getMapCache(name);
  }

  public void set(final String key, final JsonMap map) {
    set(key, ugly(map));
  }

  public void set(final String key, final String value) {
    set(key, value, 0, null);
  }

  public void set(final String key, final String value, final int timeout, final TimeUnit unit) {
    rMap.put(key, value, timeout, unit);
  }

  public void set(final String key, final JsonMap map, final int timeout, final TimeUnit unit) {
    set(key, ugly(map), timeout, unit);
  }

  public void set(final String key, final JsonMap map, final LocalDateTime expire) {
    set(key, ugly(map), expire);
  }

  public void set(final String key, final String value, final LocalDateTime expire) {
    var now = LocalDateTime.now();
    rMap.put(key, value, ChronoUnit.SECONDS.between(now, expire), TimeUnit.SECONDS);
  }

  public void set(final String key, final JsonList list) {
    set(key, ugly(list));
  }

  public void set(final String key, final Boolean bool) {
    set(key, bool.toString());
  }

  public void unset(final String key) {
    rMap.removeAsync(key);
  }

  public boolean del(final String key) {
    return rMap.remove(key) != null;
  }

  public Boolean getBoolean(final String key) {
    final String string = get(key);
    return isEmpty(string) ? null:Boolean.valueOf(string);
  }

  public String get(final String key) {
    return rMap.get(key);
  }

  public JsonMap getMap(final String key) {
    return JsonMap.parse(get(key));
  }

  public JsonList getList(final String key) {
    return new JsonList(get(key), 1);
  }

  public void setAll(final Map<String, ? extends Json> map) {
    rMap.putAll(map.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> ugly(e.getValue()))));
  }

}
