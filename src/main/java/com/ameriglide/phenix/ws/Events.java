package com.ameriglide.phenix.ws;

import com.ameriglide.phenix.common.Agent;
import com.ameriglide.phenix.common.Ticket;
import com.ameriglide.phenix.core.Iterables;
import com.ameriglide.phenix.core.Log;
import com.ameriglide.phenix.core.Optionals;
import jakarta.servlet.http.HttpSession;
import jakarta.websocket.*;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;
import net.inetalliance.potion.Locator;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonList;
import net.inetalliance.types.json.JsonMap;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.ameriglide.phenix.servlet.Startup.topics;
import static java.util.Collections.emptyList;


@ServerEndpoint(value = "/events", configurator = Events.Configurator.class)
public class Events extends Endpoint {

  private static final Log log = new Log();
  private static final Map<Integer, List<Session>> sessions = new ConcurrentHashMap<>();
  public static Function<Session, MessageHandler> handler =
    session -> (MessageHandler.Whole<String>) message -> log.debug(
    () -> "session [%s] new message %s".formatted(session.getId(), message));

  public static Ticket getTicket(final Agent agent) {
    return sessions.getOrDefault(agent.id, emptyList()).stream().findFirst().map(Events::getTicket).orElse(null);
  }

  public static Ticket getTicket(final Session session) {
    if (session==null) {
      return null;
    }
    var http = (HttpSession) session.getUserProperties().get(HttpSession.class.getName());
    if (http==null) {
      return null;
    }
    try {
      return (Ticket) http.getAttribute("ticket");
    } catch (IllegalStateException e) {
      invalidate(session);
    }
    return null;
  }

  private static void invalidate(final Session session) {
    sessions.values().forEach(list -> list.remove(session));
  }


  public static void init() {
    log.info(() -> "watching the events topic");
    topics.events().listen((channel, msg) -> {
      var agentId = msg.getInteger("agent");
      var type = msg.get("type");
      var event = msg.getMap("event");
      log.debug(() -> "new %s event for %s".formatted(type,
        agentId==null ? "twilio":Locator.$(new Agent(agentId)).getFullName()));
      log.trace(() -> Json.pretty(event));
      SessionHandler.getHandler(type).onAsyncMessage(sessions.get(agentId), event);
    });

  }

  public static void broadcast(final String type, final Integer principal, final Json msg) {
    if (principal==null) {
      log.trace(() -> "broadcasting [%s] from %d, msg: %s".formatted(type, principal, trace(msg, 2)));
      // tell everyone
      sessions.values().stream().flatMap(Iterables::stream).forEach(session -> send(session, type, msg));
    } else {
      log.trace(() -> "shallowcasting [%s] from %d, msg: %s".formatted(type, principal, trace(msg, 3)));
      // tell only the sockets for that agent
      sessions
        .computeIfAbsent(principal, a -> new CopyOnWriteArrayList<>())
        .forEach(session -> send(session, type, msg));
    }
  }

  private static String trace(Json json, int limit) {
    if (json instanceof JsonList list) {
      var examples = "[" + list.stream().limit(limit).map(Json::ugly).collect(Collectors.joining(","));
      var size = list.size();
      if (size > limit) {
        examples += ", ... " + size;
      }
      return examples + "]";
    }
    return Json.ugly(json);
  }

  public static void send(final Session session, final String type, final Json msg) {
    if (msg!=null) {
      try {
        if (session.isOpen()) {
          session.getBasicRemote().sendText(Json.ugly(new JsonMap().$("type", type).$("msg", msg)));
        }
      } catch (Throwable t) {
        log.debug(() -> "cannot write to session for %s".formatted(getTicket(session).principal), t);
        invalidate(session);
      }
    }
  }

  public static void destroy() {
    sessions.clear();
  }

  public static Map<TimeZone, Set<Ticket>> getActiveAgents() {
    return sessions
      .values()
      .stream()
      .map(l -> l.iterator().next())
      .map(Events::getTicket)
      .reduce(new HashMap<>(), (map, ticket) -> {
        map.computeIfAbsent(ticket.getTimeZone(), t -> new HashSet<>()).add(ticket);
        return map;
      }, (result, partial) -> {
        result.putAll(partial);
        return result;
      });
  }

  public static void sendReliably(final List<Session> sessions, final String type, final JsonMap msg) {
    var newestFirst = sessions.listIterator(sessions.size());
    while (newestFirst.hasPrevious()) {
      var session = newestFirst.previous();
      if (session.isOpen()) {
        try {
          send(session, type, msg);
          return;
        } catch (Throwable t) {
          log.warn(t);
        }
      }
    }
    log.warn(() -> "no available sessions for msg of type %s: %s".formatted(type, Json.pretty(msg)));
  }

  @Override
  public void onOpen(final Session session, final EndpointConfig config) {
    var ticket = getTicket(session);
    if (ticket!=null) {
      sessions.computeIfAbsent(ticket.id, u -> new CopyOnWriteArrayList<>()).add(session);
      log.trace(() -> "%s connected".formatted(ticket.principal));
      session.addMessageHandler(handler.apply(session));
    }
  }

  @Override
  public void onClose(final Session session, final CloseReason closeReason) {
    var ticket = getTicket(session);
    if (ticket!=null) {
      Optionals.of(sessions.get(ticket.id)).ifPresent(l -> l.remove(session));
      log.trace(() -> "%s disconnected (%s - %s)".formatted(ticket.principal, closeReason.getCloseCode(),
        closeReason.getReasonPhrase()));
    }
  }

  @Override
  public void onError(final Session session, final Throwable thr) {
    super.onError(session, thr);
    if (thr instanceof IOException) {
      log.trace(() -> "closing session %s".formatted(session.getId()));
      Optionals.of(getTicket(session)).map(ticket -> sessions.get(ticket.id)).ifPresent(l -> l.remove(session));
    }
  }

  public static class Configurator extends ServerEndpointConfig.Configurator {

    @Override
    public void modifyHandshake(final ServerEndpointConfig config, final HandshakeRequest request,
                                final HandshakeResponse response) {
      final HttpSession session = (HttpSession) request.getHttpSession();
      if (session!=null) {
        config.getUserProperties().put(HttpSession.class.getName(), session);
      }
    }
  }

}
