package com.ameriglide.phenix.api;

import com.ameriglide.phenix.Auth;
import com.ameriglide.phenix.Startup;
import com.ameriglide.phenix.common.Agent;
import com.ameriglide.phenix.common.Call;
import com.ameriglide.phenix.servlet.PhenixServlet;
import com.ameriglide.phenix.servlet.TwiMLServlet;
import com.ameriglide.phenix.types.CallDirection;
import com.ameriglide.phenix.types.Resolution;
import com.twilio.type.PhoneNumber;
import com.twilio.type.Sip;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.log.Log;
import net.inetalliance.potion.Locator;

import static com.ameriglide.phenix.servlet.TwiMLServlet.asParty;
import static java.time.LocalDateTime.now;
import static net.inetalliance.funky.StringFun.isEmpty;

@WebServlet("/api/voice/dial")
public class VoiceDial extends PhenixServlet {

  @Override
  protected void get(HttpServletRequest request, HttpServletResponse response) throws Exception {
    var agent = request.getParameter("agent");
    var number = request.getParameter("number");
    var caller = asParty(Auth.getAgent(request));
    TwiMLServlet.Party called;
    if (isEmpty(agent)) {
      if (isEmpty(number)) {
        throw new IllegalArgumentException();
      }
      called = asParty(new PhoneNumber(number));
    } else {
      called = asParty(Locator.$(new Agent(Integer.parseInt(agent))));
    }
      // create new Call with twilio
      var from = new Sip(asParty(Auth.getAgent(request)).sip());
      var call =
        new Call(Startup.router.call(from, "/twilio/voice/dial", request.getQueryString()).getSid());
      call.setAgent(caller.agent());
      caller.setCNAM(call);
      call.setDirection(called.isAgent() ? CallDirection.INTERNAL : CallDirection.OUTBOUND);
      call.setCreated(now());
      call.setResolution(Resolution.ACTIVE);
      Locator.create("VoiceDial", call);
      log.info("New API dial %s %s -> %s", call.sid, caller.endpoint(), called.endpoint());
      response.sendError(HttpServletResponse.SC_NO_CONTENT);

  }
  private static final Log log = Log.getInstance(VoiceDial.class);
}
