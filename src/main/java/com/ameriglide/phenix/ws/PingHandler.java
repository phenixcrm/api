package com.ameriglide.phenix.ws;

import com.ameriglide.phenix.core.Log;
import jakarta.websocket.Session;
import net.inetalliance.types.json.JsonMap;

public class PingHandler implements JsonMessageHandler {

    @Override
    public JsonMap onMessage(final Session session, final JsonMap msg) {
        log.trace(() -> "%s pinged us".formatted(Events.getTicket(session).principal()));
        return null;
    }

    private static final Log log = new Log();
}
