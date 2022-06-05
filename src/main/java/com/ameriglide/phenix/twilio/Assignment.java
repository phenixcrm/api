package com.ameriglide.phenix.twilio;

import com.ameriglide.phenix.PhenixServlet;
import com.ameriglide.phenix.common.Agent;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.potion.Locator;
import net.inetalliance.types.json.JsonMap;

import static com.ameriglide.phenix.twilio.TwiMLServlet.asParty;

@WebServlet("/twilio/assignment")
public class Assignment extends PhenixServlet {
  @Override
  protected void post(HttpServletRequest request, HttpServletResponse response) throws Exception {
    var agent = Locator.$1(Agent.withTwilioSid(request.getParameter("WorkerSid")));
    respond(response, new JsonMap()
      .$("instruction","conference")
      .$("to",asParty(agent).sip()));
  }
}
