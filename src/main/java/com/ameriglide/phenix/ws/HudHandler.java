package com.ameriglide.phenix.ws;

import jakarta.websocket.Session;
import net.inetalliance.log.Log;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Pattern;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class HudHandler
    implements Runnable, JsonMessageHandler {

  private static final Map<String, HudStatus> status;
  private static final Pattern sip = Pattern.compile("SIP/(7[0-9][0-9][0-9]).*");
  private static final transient Log log = Log.getInstance(HudHandler.class);

  static {
    status = new HashMap<>();
  }

  private final JsonMap hud;
  private final Set<Session> subscribers;
  private final ExecutorService service = Executors.newFixedThreadPool(4, (r)->{
    var t = new Thread(r);
    t.setDaemon(true);
    return t;
  });
  private final ScheduledExecutorService scheduler = Executors
      .newSingleThreadScheduledExecutor((r)->{
        var t = new Thread(r);
        t.setDaemon(true);
        return t;
      });

  HudHandler() {
    subscribers = Collections.synchronizedSet(new HashSet<>(8));
    hud = new JsonMap(true);
    scheduler.scheduleWithFixedDelay(this, 0, 250, MILLISECONDS);

  }

  static HudStatus getStatus(final String agent) {
    return status.get(agent);

  }

  @Override
  public JsonMap onMessage(final Session session, final JsonMap msg) {
    try {
      final Action action = Action.valueOf(msg.get("action").toUpperCase());
      switch (action) {
        case SUBSCRIBE:
          subscribers.add(session);
          return hud;
        case UNSUBSCRIBE:
          subscribers.remove(session);
      }
    } catch (IllegalArgumentException e) {
      log.error(e);
    }
    return null;
  }

  @Override
  public void destroy() {
    hud.clear();
    scheduler.shutdownNow();
  }

  @Override
  public void run() {
    for (HudStatus value : status.values()) {
      value.direction = null;
      value.callId = null;
    }

    JsonMap current = new JsonMap();
    for (final Map.Entry<String, HudStatus> entry : status.entrySet()) {
      current.put(entry.getKey(),
          new JsonMap().$("direction", entry.getValue().direction)
              .$("available", entry.getValue().available));
    }

    if (!current.equals(hud)) {
      hud.clear();
      hud.putAll(current);
      broadcast(new JsonMap().$("type", "hud").$("msg", this.hud));
    }
  }


  private void broadcast(final JsonMap msg) {
    final CountDownLatch latch = new CountDownLatch(subscribers.size());
    final Collection<Session> toRemove = new ArrayList<>(0);
    try {
      for (final Session subscriber : subscribers) {
        service.submit(() -> {
          try {
            subscriber.getBasicRemote().sendText(Json.ugly(msg));
          } catch (IOException e) {
            toRemove.add(subscriber);
          } finally {
            latch.countDown();
          }
        });
      }
      latch.await(1, SECONDS);
    } catch (InterruptedException e) {
      // oh well, we'll get 'em next time
    } finally {
      // remove any dead ones we found
      subscribers.removeAll(toRemove);
    }
  }

  enum Action {
    SUBSCRIBE,
    UNSUBSCRIBE
  }

}
