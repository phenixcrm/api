package com.ameriglide.phenix.api;

import com.ameriglide.phenix.Auth;
import com.ameriglide.phenix.common.*;
import com.ameriglide.phenix.servlet.PhenixServlet;
import com.ameriglide.phenix.servlet.Startup;
import com.ameriglide.phenix.servlet.TwiMLServlet;
import com.ameriglide.phenix.servlet.exception.NotFoundException;
import com.ameriglide.phenix.types.CallDirection;
import com.ameriglide.phenix.types.Resolution;
import com.twilio.type.PhoneNumber;
import com.twilio.type.Sip;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.log.Log;
import net.inetalliance.potion.Locator;

import java.util.Objects;

import static com.ameriglide.phenix.servlet.TwiMLServlet.asParty;
import static java.time.LocalDateTime.now;
import static net.inetalliance.funky.Funky.of;
import static net.inetalliance.funky.StringFun.isEmpty;
import static net.inetalliance.funky.StringFun.isNotEmpty;
import static net.inetalliance.potion.Locator.$;
import static net.inetalliance.potion.Locator.$1;

@WebServlet({"/api/voice/dial", "/api/dial"})
public class VoiceDial extends PhenixServlet {

  @Override
  protected void get(HttpServletRequest request, HttpServletResponse response) throws Exception {

    var agent = request.getParameter("agent");
    var number = request.getParameter("number");
    CNAM.CallerIdSource cid  ;

    TwiMLServlet.Party called;
    if (isEmpty(agent)) {
      if (isEmpty(number)) {
        throw new IllegalArgumentException();
      }
      called = asParty(new PhoneNumber(number));
    } else {
      called = asParty($(new Agent(Integer.parseInt(agent))));
    }
    // create new Call with twilio
    var from = new Sip(asParty(Auth.getAgent(request)).sip());
    var call =
      new Call(Startup.router.call(from, "/twilio/voice/dial", request.getQueryString()).getSid());
    var callingAgent = Auth.getAgent(request);
    call.setAgent(callingAgent);
    call.setDirection(called.isAgent() ? CallDirection.INTERNAL : CallDirection.OUTBOUND);
    call.setCreated(now());
    call.setResolution(Resolution.ACTIVE);
    var lead = request.getParameter("lead");
    if (isNotEmpty(lead)) {
      var opp = $(new Opportunity(Integer.valueOf(lead)));
      if (opp == null) {
        throw new NotFoundException();
      }
      call.setOpportunity(opp);
      var b = opp.getBusiness();
      call.setBusiness(b);
      cid =
        of($1(SkillQueue.withProduct(opp.getProductLine()).and(SkillQueue.withBusiness(b))))
          .stream()
          .filter(Objects::nonNull)
          .peek(call::setQueue)
          .map(q -> $1(VerifiedCallerId.withQueue(q)))
          .filter(Objects::nonNull)
          .findFirst()
          .orElseGet(() -> $1(VerifiedCallerId.isDefault));
    } else if (called.isAgent()) {
      cid = callingAgent;
    } else {
      cid = $1(VerifiedCallerId.isDefault);
    }
    cid.setPhoneNumber(call);
    Locator.create("VoiceDial", call);
    log.info("New API dial %s %s -> %s", call.sid, callingAgent.getSipUser(), called.endpoint());
    response.sendError(HttpServletResponse.SC_NO_CONTENT);
  }

  private static final Log log = Log.getInstance(VoiceDial.class);
}
