package com.ameriglide.phenix.ws;

import jakarta.websocket.Session;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class DigiHandler implements JsonMessageHandler{
  private final Set<Session> subscribers = Collections.synchronizedSet(new HashSet<>(8));
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
