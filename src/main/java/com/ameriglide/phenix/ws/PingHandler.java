package com.ameriglide.phenix.ws;

import jakarta.websocket.Session;
import net.inetalliance.types.json.JsonMap;

import static net.inetalliance.util.shell.Shell.log;

public class PingHandler
    implements JsonMessageHandler {

  @Override
  public JsonMap onMessage(final Session session, final JsonMap msg) {
    log.trace(()->"%s pinged us".formatted(Events.getTicket(session).principal()));
    return null;
  }
}
