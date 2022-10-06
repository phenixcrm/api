package com.ameriglide.phenix.ws;

import jakarta.websocket.Session;
import net.inetalliance.types.json.JsonMap;

public record PopHandler(StatusHandler status) implements JsonMessageHandler{
    @Override
    public JsonMap onMessage(final Session session, final JsonMap msg) {
        Events.send(session,"pop",msg); // pop to the latest session
        return status.onConnect(session); // send status updates to every session
    }
}
