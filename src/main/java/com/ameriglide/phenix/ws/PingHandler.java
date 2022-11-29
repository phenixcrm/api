package com.ameriglide.phenix.ws;

import jakarta.websocket.Session;
import net.inetalliance.types.json.JsonMap;

import java.util.List;

import static net.inetalliance.util.shell.Shell.log;

public class PingHandler implements JsonMessageHandler {

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
