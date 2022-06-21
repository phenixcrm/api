package com.ameriglide.phenix.ws;

import com.ameriglide.phenix.TaskRouter;
import com.ameriglide.phenix.common.Agent;
import com.ameriglide.phenix.common.Call;
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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static net.inetalliance.funky.StringFun.isNotEmpty;
import static net.inetalliance.potion.Locator.forEach;

public class HudHandler
  implements Runnable, JsonMessageHandler {

  private static final Map<Integer, HudStatus> status;
  private static final Log log = Log.getInstance(HudHandler.class);

  static {
    status = new HashMap<>();
  }

  private final JsonMap hud;
  private final Set<Session> subscribers;
  private final ExecutorService service = Executors.newFixedThreadPool(4, (r) -> {
    var t = new Thread(r);
    t.setDaemon(true);
    return t;
  });
  private final ScheduledExecutorService scheduler = Executors
    .newSingleThreadScheduledExecutor((r) -> {
      var t = new Thread(r);
      t.setDaemon(true);
      return t;
    });
  private final TaskRouter router;

  HudHandler(TaskRouter router) {
    this.router = router;
    subscribers = Collections.synchronizedSet(new HashSet<>(8));
    hud = new JsonMap(true);
    scheduler.scheduleWithFixedDelay(this, 0, 250, MILLISECONDS);

  }

  @Override
  public JsonMap onMessage(final Session session, final JsonMap msg) {
    try {
      final Action action = Action.valueOf(msg.get("action").toUpperCase());
      switch (action) {
        case SUBSCRIBE -> {
          subscribers.add(session);
          log.info("Added HUD subscription for %s, #:%d",Events.getTicket(session).agent().getSipUser(),
            subscribers.size());
          return hud;
        }
        case UNSUBSCRIBE -> {
          subscribers.remove(session);
          log.info("Removed HUD subscription for %s, #:%d",Events.getTicket(session).agent().getSipUser(),
            subscribers.size());
        }
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
    try {
      for (var value : status.values()) {
        value.direction = null;
        value.callId = null;
      }
      updateCalls();
      updateAvailability();
      var current = new JsonMap();
      for (final var entry : status.entrySet()) {
        var agentStatus = entry.getValue();
        current.put(Integer.toString(entry.getKey()),
          new JsonMap()
            .$("direction", agentStatus.direction)
            .$("callId", agentStatus.callId)
            .$("available", agentStatus.available));
      }

      if (!current.equals(hud)) {
        hud.clear();
        hud.putAll(current);
        log.info("Hud changed, broadcasting updates");
        broadcast(new JsonMap().$("type", "hud").$("msg", this.hud));
      }
    } catch(Throwable t) {
      log.error(t);
    }
  }

  private void updateCalls() {

    forEach(Call.isActive, call -> call.getActiveAgents().forEach(a-> {
      var agentStatus = status.computeIfAbsent(a.id,k-> new HudStatus());
      agentStatus.direction = call.getDirection();
      agentStatus.callId = call.sid;
      agentStatus.available = router.byAgent.get(a.getSid());

    }));
  }


  private void updateAvailability() {
    forEach(Agent.isActive, agent -> {
      HudStatus hudStatus = status.computeIfAbsent(agent.id, k -> new HudStatus());
      if (isNotEmpty(agent.getSid()) && router.byAgent
        .computeIfAbsent(agent.getSid(), sid->router.getWorker(sid).getAvailable()) != hudStatus.available) {
        hudStatus.available = !hudStatus.available;
      }
    });
  }


  private void broadcast(final JsonMap msg) {
    final var latch = new CountDownLatch(subscribers.size());
    final var toRemove = new ArrayList<Session>(0);
    try {
      for (final var subscriber : subscribers) {
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
      if (!latch.await(1, SECONDS)) {
        log.warning("slow status broadcast");
      }
    } catch (InterruptedException e) {
      // oh well, we'll get 'em next time
    } finally {
      // remove any dead ones we found
      toRemove.forEach(subscribers::remove);
    }
  }

  enum Action {
    SUBSCRIBE,
    UNSUBSCRIBE
  }

}
