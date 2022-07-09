package com.ameriglide.phenix.ws;

import com.ameriglide.phenix.common.Ticket;
import jakarta.servlet.http.HttpSession;
import jakarta.websocket.*;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;
import net.inetalliance.funky.Funky;
import net.inetalliance.log.Log;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import static net.inetalliance.log.Log.getInstance;

@ServerEndpoint(value = "/events", configurator = Events.Configurator.class)
public class Events
  extends Endpoint {

  private final static Lock lock = new ReentrantLock();
  private static final Log log = getInstance(Events.class);
  public static Function<Session, MessageHandler> handler = session ->
    (MessageHandler.Whole<String>) message -> log.debug("session [%s] new message %s", session.getId(), message);
  private static final Map<Integer, List<Session>> sessions = Collections
    .synchronizedMap(new HashMap<>());

  public static Ticket getTicket(final Session session) {
    return (Ticket) ((HttpSession)session.getUserProperties().get(HttpSession.class.getName()))
      .getAttribute("ticket");
  }

  public static void init() {
  }

  public static void destroy() {
    sessions.clear();
  }

  public static void sendToLatest(final String type, final Integer agent, final Json msg) {
    lock.lock();
    try {
      final List<Session> sessions = Events.sessions.getOrDefault(agent,Collections.emptyList());
      if (!sessions.isEmpty()) {
        send(sessions.get(sessions.size() - 1), type, msg);
      }
    } finally {
      lock.unlock();
    }
  }

  public static void send(final Session session, final String type, final Json msg) {
    if (msg != null) {
      try {
        if (session.isOpen()) {
          session.getBasicRemote().sendText(Json.ugly(new JsonMap().$("type", type).$("msg", msg)));
        }
      } catch (IOException e) {
        log.debug("cannot write to closed session for %s", getTicket(session).principal());
      }
    }
  }

  public static void broadcast(final String type, final Integer principal, final Json msg) {
    lock.lock();
    try {
      if (principal == null) {
        // tell everyone
        sessions.values().stream().flatMap(Funky::stream)
          .forEach(session -> send(session, type, msg));
      } else {
        // tell only the sockets for that agent
        sessions.computeIfAbsent(principal, a -> new ArrayList<>())
          .forEach(session -> send(session, type, msg));
      }
    } finally {
      lock.unlock();
    }
  }

  public static Set<Integer> getActiveAgents() {
    lock.lock();
    try {
      return new HashSet<>(sessions.keySet());
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void onOpen(final Session session, final EndpointConfig config) {
    var ticket = getTicket(session);
    if(ticket != null) {
      lock.lock();
      try {
        sessions.computeIfAbsent(ticket.id(), u -> new LinkedList<>()).add(session);
      } finally {
        lock.unlock();
      }
      log.trace("%s connected", ticket.principal());
      session.addMessageHandler(handler.apply(session));
    }
  }

  @Override
  public void onClose(final Session session, final CloseReason closeReason) {
    var ticket = getTicket(session);
    if(ticket != null) {
      lock.lock();
      try {
        Funky.of(sessions.get(ticket.id())).ifPresent(l -> l.remove(session));
        log.trace("%s disconnected", ticket.principal());
      } finally {
        lock.unlock();
      }
    }
  }

  public static class Configurator
    extends ServerEndpointConfig.Configurator {

    @Override
    public void modifyHandshake(final ServerEndpointConfig config, final HandshakeRequest request,
                                final HandshakeResponse response) {
      final HttpSession session = (HttpSession) request.getHttpSession();
      config.getUserProperties().put(HttpSession.class.getName(),session);
    }
  }

}
