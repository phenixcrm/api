package com.ameriglide.phenix.api;

import com.ameriglide.phenix.Auth;
import com.ameriglide.phenix.Startup;
import com.ameriglide.phenix.common.*;
import com.ameriglide.phenix.servlet.PhenixServlet;
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

import static com.ameriglide.phenix.servlet.TwiMLServlet.asParty;
import static java.time.LocalDateTime.now;
import static net.inetalliance.funky.StringFun.isEmpty;
import static net.inetalliance.funky.StringFun.isNotEmpty;

@WebServlet({"/api/voice/dial", "/api/dial"})
public class VoiceDial extends PhenixServlet {

  TwiMLServlet.Party fromLead(final HttpServletRequest req) {

    return null;
  }

  @Override
  protected void get(HttpServletRequest request, HttpServletResponse response) throws Exception {

    var agent = request.getParameter("agent");
    var number = request.getParameter("number");

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
    var callingAgent = Auth.getAgent(request);
    call.setAgent(callingAgent);
    call.setDirection(called.isAgent() ? CallDirection.INTERNAL : CallDirection.OUTBOUND);
    call.setCreated(now());
    call.setResolution(Resolution.ACTIVE);
    var lead = request.getParameter("lead");
    var setCNAM = false;
    if (isNotEmpty(lead)) {
      var opp = Locator.$(new Opportunity(Integer.valueOf(lead)));
      if (opp == null) {
        throw new NotFoundException();
      }
      call.setOpportunity(opp);
      var b = opp.getBusiness();
      call.setBusiness(b);
      var q = Locator.$1(SkillQueue.withProduct(opp.getProductLine()).and(SkillQueue.withBusiness(b)));
      if (q == null) {
        log.warning("Could not find queue for %s (%s)", b.getName(), opp.getProductLine().getName());
      } else {
        var cid = Locator.$1(VerifiedCallerId.withQueue(q));
        call.setQueue(q);
        if (cid == null) {
          log.warning("Could not find Verified CallerID mapping for %s(%s)", q.getName(), q.id);
        } else {
          TwiMLServlet.asParty(new PhoneNumber(cid.getPhoneNumber())).setCNAM(call);
          setCNAM = true;
        }
      }
    }
    if(!setCNAM) {
      TwiMLServlet.asParty(callingAgent).setCNAM(call);
    }
    Locator.create("VoiceDial", call);
    log.info("New API dial %s %s -> %s", call.sid, callingAgent.getSipUser(), called.endpoint());
    response.sendError(HttpServletResponse.SC_NO_CONTENT);

  }

  private static final Log log = Log.getInstance(VoiceDial.class);
}
