package com.ameriglide.phenix.api;

import com.ameriglide.phenix.common.Agent;
import com.ameriglide.phenix.common.Call;
import com.ameriglide.phenix.common.Leg;
import com.ameriglide.phenix.common.Opportunity;
import com.ameriglide.phenix.core.Log;
import com.ameriglide.phenix.servlet.PhenixServlet;
import com.ameriglide.phenix.servlet.Startup;
import com.ameriglide.phenix.servlet.TwiMLServlet;
import com.ameriglide.phenix.ws.Events;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.potion.Locator;
import net.inetalliance.types.json.JsonMap;

import java.time.LocalDateTime;

import static net.inetalliance.potion.Locator.update;

@WebServlet("/api/assignment")
public class Assignment extends PhenixServlet {
  @Override
  protected void post(HttpServletRequest request, HttpServletResponse response) throws Exception {
    var reservation = request.getParameter("ReservationSid");
    var task = request.getParameter("TaskSid");
    var agent = Locator.$1(Agent.withSid(request.getParameter("WorkerSid")));
    var attributes = JsonMap.parse(request.getParameter("TaskAttributes"));
    var callSid = attributes.get("call_sid");
    if(callSid == null) {
      callSid = task;
    }
    var finalCallSid = callSid;
    log.info(()->"ASSIGN %s %s to %s".formatted(finalCallSid, attributes.get("caller"), agent.getFullName()));
    var call = Locator.$(new Call(callSid));
    update(call,"Assignment",copy -> {
      copy.setAgent(agent);
      copy.setBlame(agent);
    });
    var leg = new Leg(call,reservation);
    leg.setAgent(agent);
    leg.setCreated(LocalDateTime.now());
    Locator.create("Assignment",leg);
    if(attributes.containsKey("VoiceCall")) {
      Startup.router.conference(callSid, task, reservation);
      var qs = "TaskSid=%s&ReservationSid=%s&Assignment=%s".formatted(task, reservation, callSid);
      PhenixServlet.respond(response, new JsonMap()
        .$("instruction", "call")
        .$("timeout", 15)
        .$("record", "record-from-answer")
        .$("url",
          Startup.router.getAbsolutePath("/twilio/voice/callAgent", qs).toString())
        .$("statusCallbackUrl", Startup.router.getAbsolutePath("/twilio/voice/callAgent", qs).toString())
        .$("to", TwiMLServlet.asParty(agent).sip()));
    } else {
      var opp = attributes.containsKey("Lead") ?
              Locator.$(new Opportunity(Integer.valueOf(attributes.get("Lead")))) : call.getOpportunity();
      if(opp == null) {
        log.error(()->"Could not find opp for assignment: %s".formatted(attributes));
      }  else if(Agent.system().equals(opp.getAssignedTo())) {
        update(opp, "Assignment", copy -> {
          copy.setAssignedTo(agent);
        });
      }
      PhenixServlet.respond(response, new JsonMap().$("instruction","accept"));
      Startup.router.completeTask(task);
    }
    Events.sendToLatest("pop",agent.id,new JsonMap().$("callId",callSid));
  }

  private static final Log log = new Log();
}
