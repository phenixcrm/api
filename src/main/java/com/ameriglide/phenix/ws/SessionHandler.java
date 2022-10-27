package com.ameriglide.phenix.ws;

import com.ameriglide.phenix.api.Hud;
import com.ameriglide.phenix.twilio.TaskRouter;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.ameriglide.phenix.ws.Events.send;
import static net.inetalliance.util.shell.Shell.log;

public record SessionHandler(Session session)
  implements MessageHandler.Whole<String> {

  public static Hud hud;
  private static final Map<String, JsonMessageHandler> handlers = new ConcurrentHashMap<>();

  public SessionHandler(final Session session) {
    this.session = session;
    handlers.forEach((type, handler) -> send(session, type, handler.onConnect(session)));
  }

  public static void init(final TaskRouter router) {
    Events.init();
    var status = new StatusHandler();
    handlers.put("status", status);
    handlers.put("pop", new PopHandler(status));
    handlers.put("reminder", new ReminderHandler());
    handlers.put("ping", new PingHandler());
    handlers.put("hud", new HudHandler(hud));

  }

  public static void destroy() {
    handlers.values().forEach(JsonMessageHandler::destroy);
    Events.destroy();
  }

  public static JsonMessageHandler getHandler(final String type) {
    return handlers.computeIfAbsent(type, key -> (session, msg) -> {
      log.warn(()->"received message of unknown type %s: %s".formatted(key, Json.pretty(msg)));
      return null;
    });
  }

  @Override
  public void onMessage(final String message) {
    final JsonMap msg = JsonMap.parse(message);
    final String type = msg.get("type");
    send(session, type, getHandler(type).onMessage(session, msg.getMap("msg")));

  }
}
