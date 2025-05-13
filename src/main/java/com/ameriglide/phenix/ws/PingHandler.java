package com.ameriglide.phenix.ws;

import com.ameriglide.phenix.core.Log;
import jakarta.websocket.Session;
import net.inetalliance.types.json.JsonMap;

import java.util.List;


public class PingHandler implements JsonMessageHandler {
  private static final Log log = new Log();

  @Override
  public JsonMap onMessage(final Session session, final JsonMap msg) {
    log.trace(() -> "%s pinged us".formatted(Events.getTicket(session).principal));
    return null;
  }

  @Override
  public void onAsyncMessage(final List<Session> sessions, final JsonMap msg) {
    log.warn(() -> "Somehow ping was sent an async message");
  }
}
