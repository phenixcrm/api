package com.ameriglide.phenix.ws;

import com.ameriglide.phenix.Startup;
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
import net.inetalliance.types.json.JsonMap;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;

import static java.util.Collections.emptyList;


@ServerEndpoint(value = "/events", configurator = Events.Configurator.class)
public class Events extends Endpoint {

    private static final Log log = new Log();
    private static final Map<Integer, List<Session>> sessions = Collections.synchronizedMap(new HashMap<>());
    public static Function<Session, MessageHandler> handler =
            session -> (MessageHandler.Whole<String>) message -> log.debug(
            () -> "session [%s] new message %s".formatted(session.getId(), message));

    public static Ticket getTicket(final Agent agent) {
        return sessions.getOrDefault(agent.id, emptyList()).stream().findFirst().map(Events::getTicket).orElse(null);
    }

    public static Ticket getTicket(final Session session) {
        var http = (HttpSession) session.getUserProperties().get(HttpSession.class.getName());
        if (http==null) {
            return null;
        }
        return (Ticket) http.getAttribute("ticket");
    }

    public static void init() {
        log.info(() -> "watching the events topic");
        Startup.router.getTopic("events").addListener(JsonMap.class, (channel, msg) -> {
            var agentId = msg.getInteger("agent");
            var type = msg.get("type");
            var event = msg.getMap("event");
            log.debug(() -> "new %s event for %s".formatted(type, Locator.$(new Agent(agentId)).getFullName()));
            log.trace(() -> Json.pretty(event));
            var response = SessionHandler.getHandler(type).onMessage(sessions.get(agentId).get(0), event);
            if (response!=null) {
                broadcast(type, agentId, response);
            }
        });
    }

    public static void broadcast(final String type, final Integer principal, final Json msg) {
        if (principal==null) {
            // tell everyone
            sessions.values().stream().flatMap(Iterables::stream).forEach(session -> send(session, type, msg));
        } else {
            // tell only the sockets for that agent
            sessions
                    .computeIfAbsent(principal, a -> Collections.synchronizedList(new LinkedList<>()))
                    .forEach(session -> send(session, type, msg));
        }
    }

    public static void send(final Session session, final String type, final Json msg) {
        if (msg!=null) {
            try {
                if (session.isOpen()) {
                    session.getBasicRemote().sendText(Json.ugly(new JsonMap().$("type", type).$("msg", msg)));
                }
            } catch (IOException e) {
                log.debug(() -> "cannot write to closed session for %s".formatted(getTicket(session).principal()));
            }
        }
    }

    public static void destroy() {
        sessions.clear();
    }

    public static void sendToLatest(final String type, final Integer agent, final Json msg) {
        final var sessions = Events.sessions.getOrDefault(agent, emptyList());
        if (!sessions.isEmpty()) {
            send(sessions.get(sessions.size() - 1), type, msg);
        }
    }

    public static Map<TimeZone, Set<Ticket>> getActiveAgents() {
        return sessions
                .values()
                .stream()
                .map(l -> l.iterator().next())
                .map(Events::getTicket)
                .reduce(new HashMap<>(), (map, ticket) -> {
                    map.computeIfAbsent(ticket.timeZone(), t -> new HashSet<>()).add(ticket);
                    return map;
                }, (result, partial) -> {
                    result.putAll(partial);
                    return result;
                });
    }

    @Override
    public void onOpen(final Session session, final EndpointConfig config) {
        var ticket = getTicket(session);
        if (ticket!=null) {
            sessions.computeIfAbsent(ticket.id(), u -> Collections.synchronizedList(new LinkedList<>())).add(session);
            log.trace(() -> "%s connected".formatted(ticket.principal()));
            session.addMessageHandler(handler.apply(session));
        }
    }

    @Override
    public void onClose(final Session session, final CloseReason closeReason) {
        var ticket = getTicket(session);
        if (ticket!=null) {
            Optionals.of(sessions.get(ticket.id())).ifPresent(l -> l.remove(session));
            log.trace(() -> "%s disconnected (%s - %s)".formatted(ticket.principal(), closeReason.getCloseCode(),
                    closeReason.getReasonPhrase()));
        }
    }

    @Override
    public void onError(final Session session, final Throwable thr) {
        super.onError(session, thr);
        log.error(() -> "session error", thr);
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
