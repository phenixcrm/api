package com.ameriglide.phenix.ws;

import com.ameriglide.phenix.common.Ticket;
import jakarta.websocket.Session;
import net.inetalliance.cron.CronJob;
import net.inetalliance.cron.CronStatus;
import net.inetalliance.types.json.JsonMap;
import net.inetalliance.types.struct.maps.LazyMap;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.synchronizedMap;
import static java.util.concurrent.TimeUnit.SECONDS;
import static net.inetalliance.cron.Cron.interval;

public class StatusHandler
    implements JsonMessageHandler {

  private static final Map<String, Boolean> paused = synchronizedMap(
      new LazyMap<>(new HashMap<>(), k -> false));
  private static final Map<String, Boolean> forwarded = synchronizedMap(
      new LazyMap<>(new HashMap<>(), k -> false));

  private static final Map<String, Boolean> registered = synchronizedMap(
      new LazyMap<>(new HashMap<>(), k -> false));
  private static final Map<String, String> calls = synchronizedMap(new HashMap<>());

  public StatusHandler() {
    super();
    interval(1, SECONDS, new CronJob() {
      @Override
      public String getName() {
        return "Status Updater";
      }

      @Override
      public void exec(final CronStatus status) {
        final JsonMap map = new JsonMap();
        for (final Ticket ticket : Events.getActiveAgents()) {
          getStatus(ticket, map, false);
          if (!map.isEmpty()) {
            if (map.containsKey("callId")) {
              Events.sendToLatest("pop", ticket, new JsonMap().$("callId", map.get("callId")));
            }
            Events.broadcast("status", ticket, map);
          }
          map.clear();
        }
      }
    });
  }

  private static JsonMap getStatus(final Ticket agent, final JsonMap map, final boolean full) {
    return map;
  }

  private static void check(final String property, final String agent, final JsonMap current,
      final Map<String, Boolean> cache, final boolean defaultValue, final JsonMap changes,
      final boolean full) {
    final boolean currentValue = get(current, property, defaultValue);
    final boolean cachedValue = cache.get(agent);
    final boolean changed = currentValue != cachedValue;

    if (changed || full) {
      changes.put(property, currentValue);
    }
    if (changed) {
      cache.put(agent, currentValue);
    }
  }

  private static boolean get(final JsonMap map, final String key, final boolean defaultValue) {
    if (map == null) {
      return defaultValue;
    }
    final Boolean value = map.getBoolean(key);
    return value == null ? defaultValue : value;
  }

  private static JsonMap getStatus(final Ticket agent, final boolean full) {
    return getStatus(agent, new JsonMap(), full);
  }

  @Override
  public JsonMap onMessage(final Session session, final JsonMap map) {
    return new JsonMap();

  }

  @Override
  public JsonMap onConnect(final Session session) {
    return getStatus(Events.getTicket(session), true);
  }

  @Override
  public void destroy() {
    calls.clear();
    paused.clear();
    forwarded.clear();
    registered.clear();
  }

  private enum Action {
    PAUSE,
    FORWARD
  }
}
