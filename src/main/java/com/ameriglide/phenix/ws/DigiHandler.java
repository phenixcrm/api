package com.ameriglide.phenix.ws;

import jakarta.websocket.Session;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class DigiHandler implements JsonMessageHandler{
  private final Set<Session> subscribers = new CopyOnWriteArraySet<>();
  @Override
  public JsonMap onMessage(final Session session, final JsonMap msg) {
    return null;
  }

  public void newDigi(JsonMap msg) {
    broadcast(subscribers, Json.ugly(msg));
  }

  @Override
  public JsonMap onConnect(final Session session) {
    subscribers.add(session);
    return null;
  }

}
