package com.ameriglide.phenix.ws;

import com.ameriglide.phenix.Auth;
import com.ameriglide.phenix.common.ws.Action;
import com.ameriglide.phenix.core.Log;
import com.ameriglide.phenix.servlet.PhenixServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.websocket.Session;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;

import static com.ameriglide.phenix.servlet.Startup.router;
import static com.ameriglide.phenix.servlet.Startup.shared;

public class StatusHandler extends PhenixServlet implements JsonMessageHandler {
  private static final Log log = new Log();


  public StatusHandler() {
    super();

  }

  @Override
  public JsonMap onMessage(final Session session, final JsonMap map) {
    var ticket = Events.getTicket(session);
    log.trace(() -> "%s sent %s".formatted(ticket, Json.pretty(map)));
    switch (Action.valueOf(map.get("action").toUpperCase())) {
      case PAUSE -> {
        var worker = router.getWorker(ticket.sid());
        var available = router.available.getSid().equals(worker.getActivitySid());
        router.setActivity(worker.getSid(), available ? router.unavailable:router.available);
        log.info(
          () -> "%s switched to %s".formatted(ticket.agent().getFullName(), available ? "unavailable":"available"));
      }
      default -> throw new IllegalArgumentException();
    }
    return null;
  }

  @Override
  public JsonMap onConnect(final Session session) {
    var ticket = Events.getTicket(session);
    return ticket==null ? null:shared.availability().get(ticket.id()).toJson();
  }

  @Override
  public void destroy() {
  }

  @Override
  protected void get(HttpServletRequest request, HttpServletResponse response) throws Exception {
    respond(response, shared.availability().get(Auth.getAgent(request).id).toJson());
  }
}
