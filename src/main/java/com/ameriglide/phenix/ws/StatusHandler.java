package com.ameriglide.phenix.ws;

import com.ameriglide.phenix.api.Hud;
import com.ameriglide.phenix.common.ws.Action;
import com.ameriglide.phenix.core.Log;
import com.ameriglide.phenix.core.Optionals;
import com.ameriglide.phenix.servlet.PhenixServlet;
import com.ameriglide.phenix.twilio.TaskRouter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.websocket.Session;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;

public class StatusHandler extends PhenixServlet implements JsonMessageHandler {
    private static final Log log = new Log();
    private final TaskRouter router;


    public StatusHandler(TaskRouter router) {
        super();
        this.router = router;

    }

    @Override
    public JsonMap onMessage(final Session session, final JsonMap map) {
        var ticket = Events.getTicket(session);
        log.trace(() -> "%s sent %s".formatted(ticket, Json.pretty(map)));
        switch (Action.valueOf(map.get("action").toUpperCase())) {
            case PAUSE -> {
                var state = Hud.byAgent.get(ticket.id());
                boolean available = Optionals.of(state.getBoolean("available")).orElse(false);
                router.setActivity(ticket.sid(), available ? router.unavailable:router.available);
                router.byAgent.put(ticket.sid(), !available);
                state.put("available", !available);
                log.info(() -> "%s switched to %s".formatted(ticket.agent().getFullName(),
                        available ? "unavailable":"available"));
                return state;
            }
            case ABSENT -> {
                Boolean prev = router.byAgent.put(ticket.sid(), false);
                if (prev!=null && prev) {
                    log.info(() -> "%s missed a task and was marked absent".formatted(ticket.agent().getFullName()));
                }
            }
            case UPDATE -> {
                var state = Hud.byAgent.get(ticket.id());
                state.put("call", JsonMap.$().$("sid", map.get("callId")));
                return state;
            }
            case COMPLETE -> {
                var callSid = map.get("callId");
                Hud.byAgent.entrySet().stream().filter(e -> {
                    var call = e.getValue().getMap("call");
                    log.trace(() -> "checking %s->%s".formatted(e.getKey(), call));
                    if (call!=null) {
                        return callSid.equals(call.get("sid"));
                    }
                    return false;
                }).forEach(e -> {
                    var eState = Hud.byAgent.get(e.getKey());
                    if (eState!=null) {
                        eState.put("call", JsonMap.$().$("sid"));
                        Events.broadcast("status", e.getKey(), eState);
                    }
                });
            }
            default -> throw new IllegalArgumentException();
        }
        return null;
    }

    @Override
    public JsonMap onConnect(final Session session) {
        var ticket = Events.getTicket(session);
        return ticket==null ? null:Hud.byAgent.get(ticket.id());
    }

    @Override
    public void destroy() {
    }

    @Override
    protected void get(HttpServletRequest request, HttpServletResponse response) throws Exception {
        respond(response, Hud.map);
    }
}
