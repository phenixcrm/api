package com.ameriglide.phenix.ws;

import com.ameriglide.phenix.Auth;
import com.ameriglide.phenix.common.AgentStatus;
import com.ameriglide.phenix.common.ws.Action;
import com.ameriglide.phenix.core.Log;
import com.ameriglide.phenix.servlet.PhenixServlet;
import com.ameriglide.phenix.types.WorkerState;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.websocket.Session;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;

import java.util.List;

import static com.ameriglide.phenix.servlet.Startup.router;
import static com.ameriglide.phenix.servlet.Startup.shared;
import static com.ameriglide.phenix.types.WorkerState.AVAILABLE;
import static com.ameriglide.phenix.types.WorkerState.UNAVAILABLE;

@WebServlet("/api/status")
public class StatusHandler extends PhenixServlet implements JsonMessageHandler {
  private static final Log log = new Log();


  public StatusHandler() {
    super();

  }

  @Override
  public void destroy() {
  }

  @Override
  public JsonMap onMessage(final Session session, final JsonMap map) {
    var ticket = Events.getTicket(session);
    log.trace(() -> "%s sent %s".formatted(ticket, Json.pretty(map)));
    switch (Action.valueOf(map.get("action").toUpperCase())) {
      case PAUSE -> {
        var worker = router.getWorker(ticket.sid);
        var from = WorkerState.from(worker);
        var opposite = from==AVAILABLE ? UNAVAILABLE:AVAILABLE;
        router.setActivity(worker.getSid(), opposite.activity());
        log.info(() -> "%s switched from %s to %s".formatted(ticket.agent().getFullName(), from, opposite));
      }
      default -> throw new IllegalArgumentException();
    }
    return null;
  }

  @Override
  public void onAsyncMessage(final List<Session> sessions, final JsonMap msg) {
    if (sessions!=null && !sessions.isEmpty()) {
      var ticket = Events.getTicket(sessions.get(0));
      var status = shared.availability().computeIfAbsent(ticket.id, id -> new AgentStatus(ticket.agent()));
      if (msg.containsKey("workerState")) {
        Events.broadcast("status", ticket.id,
          status.withWorkerState(msg.getEnum("workerState",WorkerState.class)).toJson());
      }
    }
  }

  @Override
  public JsonMap onConnect(final Session session) {
    var ticket = Events.getTicket(session);
    return ticket==null ? null:shared
      .availability()
      .computeIfAbsent(ticket.id, id -> new AgentStatus((ticket.agent())))
      .toJson();
  }

  @Override
  protected void get(HttpServletRequest request, HttpServletResponse response) throws Exception {
    var agent = Auth.getAgent(request);
    respond(response, shared.availability().computeIfAbsent(agent.id, id -> new AgentStatus(agent)).toJson());
  }


}
