package com.ameriglide.phenix.ws;

import com.ameriglide.phenix.core.ExecutorServices;
import com.ameriglide.phenix.core.Log;
import jakarta.websocket.Session;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HudHandler implements JsonMessageHandler {

  private static final Log log = new Log();

  private final Set<Session> subscribers;

  HudHandler() {
    subscribers = Collections.synchronizedSet(new HashSet<>(8));

  }

  public static void shutdown() {
    ExecutorServices.shutdown("HUD Event Handler", service);
  }

  @Override
  public JsonMap onMessage(final Session session, final JsonMap msg) {
    try {
      final Action action = Action.valueOf(msg.get("action").toUpperCase());
      switch (action) {
        case SUBSCRIBE -> {
          subscribers.add(session);
          log.debug(
            () -> "Added HUD subscription for %s, #:%d".formatted(Events.getTicket(session).agent().getSipUser(),
              subscribers.size()));
          return SessionHandler.hud.json;
        }
        case UNSUBSCRIBE -> {
          subscribers.remove(session);
          log.debug(
            () -> "Removed HUD subscription for %s, #:%d".formatted(Events.getTicket(session).agent().getSipUser(),
              subscribers.size()));
        }
      }
    } catch (IllegalArgumentException e) {
      log.error(e);
    }
    return null;
  }

  @Override
  public void onAsyncMessage(final List<Session> sessions, final JsonMap jsonMsg) {
    var msg = Json.ugly(jsonMsg);
    sessions.parallelStream().forEach(session -> {
      try {
        session.getBasicRemote().sendText(msg);
      } catch (IOException e) {
        log.warn(e);
      }
    });
  }

  public void changed(final JsonMap map) {
    broadcast(new JsonMap().$("type", "hud").$("msg", map));
  }

  private void broadcast(final JsonMap msg) {
    broadcast(Json.ugly(msg));

  }

  private void broadcast(final String msg) {
    log.trace(() -> "Brodcasting HUD update");
    broadcast(subscribers, msg);
  }

  enum Action {
    SUBSCRIBE, UNSUBSCRIBE
  }

}
