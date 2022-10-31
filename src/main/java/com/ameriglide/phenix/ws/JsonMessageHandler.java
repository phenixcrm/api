package com.ameriglide.phenix.ws;

import jakarta.websocket.Session;
import net.inetalliance.types.json.JsonMap;

import java.util.List;

public interface JsonMessageHandler {

  JsonMap onMessage(final Session session, final JsonMap msg);

  default void onAsyncMessage(List<Session> sessions, final JsonMap msg) {
  }

  default JsonMap onConnect(final Session session) {
    return null;
  }

  default void destroy() {
  }

}
