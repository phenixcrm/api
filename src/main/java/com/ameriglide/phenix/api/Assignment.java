package com.ameriglide.phenix.api;

import com.ameriglide.phenix.common.Agent;
import com.ameriglide.phenix.common.Call;
import com.ameriglide.phenix.common.Leg;
import com.ameriglide.phenix.common.Opportunity;
import com.ameriglide.phenix.core.Log;
import com.ameriglide.phenix.core.Optionals;
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
    private static final Log log = new Log();

    @Override
    protected void post(HttpServletRequest request, HttpServletResponse response) throws Exception {
        var reservation = request.getParameter("ReservationSid");
        var task = request.getParameter("TaskSid");
        var agent = Locator.$1(Agent.withSid(request.getParameter("WorkerSid")));
        var attributes = JsonMap.parse(request.getParameter("TaskAttributes"));
        var callSid = Optionals.of(attributes.get("call_sid")).orElse(task);
        log.info(() -> "ASSIGN %s %s %s".formatted(callSid, attributes.get("caller"), agent.getFullName()));
        var call = Locator.$(new Call(callSid));
        update(call, "Assignment", copy -> {
            copy.setAgent(agent);
            copy.setBlame(agent);
        });
        var leg = new Leg(call, reservation);
        leg.setAgent(agent);
        leg.setCreated(LocalDateTime.now());
        Locator.create("Assignment", leg);
        if (attributes.containsKey("VoiceCall")) {
            Startup.router.conference(callSid, task, reservation);
            PhenixServlet.respond(response, new JsonMap()
                    .$("instruction", "call")
                    .$("timeout", 15)
                    .$("record", "record-from-answer")
                    .$("url", Startup.router
                            .getAbsolutePath("/twilio/voice/callAgent",
                                    "TaskSid=%s&ReservationSid=%s&Assignment=%s".formatted(task, reservation, callSid))
                            .toString())
                    .$("statusCallbackUrl", Startup.router.getAbsolutePath("/twilio/voice/callAgent", null).toString())
                    .$("to", TwiMLServlet.asParty(agent).sip()));
        } else if (attributes.containsKey("Lead")) {
            var opp = Locator.$(new Opportunity(Integer.valueOf(attributes.get("Lead"))));
            if (Agent.system().equals(opp.getAssignedTo())) {
                update(opp, "Assignment", copy -> {
                    copy.setAssignedTo(agent);
                });
            }
            PhenixServlet.respond(response, new JsonMap().$("instruction", "accept"));
            Startup.router.completeTask(task);
        }
        Events.sendToLatest("pop", agent.id, new JsonMap().$("callId", callSid));
    }
}
