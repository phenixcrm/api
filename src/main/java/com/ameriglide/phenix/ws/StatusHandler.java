package com.ameriglide.phenix.ws;

import com.ameriglide.phenix.twilio.TaskRouter;
import com.ameriglide.phenix.servlet.PhenixServlet;
import com.twilio.rest.taskrouter.v1.workspace.Activity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.websocket.Session;
import net.inetalliance.types.json.JsonMap;

public class StatusHandler extends PhenixServlet
    implements JsonMessageHandler {
  private final TaskRouter router;

  private final JsonMap status;

  public StatusHandler(TaskRouter router) {
    super();
    this.router = router;
    status = new JsonMap();
    refresh();

  }
  private void refresh() {
    synchronized(status) {
      router.getWorkers().forEach(w -> ((JsonMap)status.computeIfAbsent(w.getSid(), s->new JsonMap()))
        .$("sid", w.getSid())
        .$("name", w.getFriendlyName())
        .$("activity", w.getActivitySid()));
    }
  }

  @Override
  public JsonMap onMessage(final Session session, final JsonMap map) {
    var ticket = Events.getTicket(session);
    if(ticket == null) {
      return null;
    }
    var state = status.getMap(ticket.sid());
    switch(Action.valueOf(map.get("action").toUpperCase())) {
      case PAUSE -> {
        var current = router.bySid.get(state.get("activity"));
        final Activity newActivity;
        if(router.available.equals(current)) {
          newActivity = router.unavailable;
        } else if (router.unavailable.equals(current)) {
         newActivity = router.available;
        } else if (router.offline.equals(current)) {
         newActivity = router.available;
        } else {
          throw new RuntimeException(String.format("Unknown activity, %s", current));
        }
        var w = router.setActivity(ticket.sid(),newActivity);
        state.put("activity",w.getActivitySid());
        router.byAgent.put(w.getSid(),w.getAvailable());
        HudHandler.
      }
      case ABSENT -> {
        router.byAgent.put(ticket.sid(),false);
        if (!state.get("activity").equals(router.unavailable.getSid())) {
          state.put("activity",router.unavailable.getSid());
          return state;
        }
        return null;
      }
      default -> throw new IllegalArgumentException();
    }
    return state;

  }

  @Override
  public JsonMap onConnect(final Session session) {
    var ticket = Events.getTicket(session);
    return ticket == null ? null : status.getMap(ticket.sid());
  }

  @Override
  public void destroy() {
    status.clear();
  } private enum Action {
    PAUSE,
    FORWARD,
    ABSENT
  }

  @Override
  protected void get(HttpServletRequest request, HttpServletResponse response) throws Exception {
    respond(response,status);
  }
}
