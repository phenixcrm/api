package com.ameriglide.phenix.ws;

import com.ameriglide.phenix.api.Hud;
import com.ameriglide.phenix.core.Log;
import com.ameriglide.phenix.servlet.PhenixServlet;
import com.ameriglide.phenix.twilio.TaskRouter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.websocket.Session;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;

public class StatusHandler extends PhenixServlet
    implements JsonMessageHandler {
  private final TaskRouter router;


  public StatusHandler(TaskRouter router) {
    super();
    this.router = router;

  }

  @Override
  public JsonMap onMessage(final Session session, final JsonMap map) {
    var ticket = Events.getTicket(session);
    log.trace(()->"%s sent %s".formatted(ticket,Json.pretty(map)));
    if(ticket == null) {
      return null;
    }
    var state = Hud.byAgent.get(ticket.id());
    boolean available = state.getBoolean("available");
    switch(Action.valueOf(map.get("action").toUpperCase())) {
      case PAUSE -> {
        router.setActivity(ticket.sid(), available ? router.unavailable : router.available);
        router.byAgent.put(ticket.sid(),!available);
        log.info(()->"%s switched to %s".formatted(ticket.agent().getFullName(), available ? "unavailable" : "available"));
      }
      case ABSENT -> {
        router.byAgent.put(ticket.sid(),false);
        log.info(()->"%s missed a task and was marked absent".formatted(ticket.agent().getFullName()));
      }
      default -> throw new IllegalArgumentException();
    }
    return state;

  }

  @Override
  public JsonMap onConnect(final Session session) {
    var ticket = Events.getTicket(session);
    return ticket == null ? null : Hud.byAgent.get(ticket.id());
  }

  @Override
  public void destroy() {
  } private enum Action {
    PAUSE,
    FORWARD,
    ABSENT
  }

  @Override
  protected void get(HttpServletRequest request, HttpServletResponse response) throws Exception {
    respond(response,Hud.map);
  }
  private static final Log log = new Log();
}
