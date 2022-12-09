package com.ameriglide.phenix.api;

import com.ameriglide.phenix.Auth;
import com.ameriglide.phenix.common.*;
import com.ameriglide.phenix.core.Log;
import com.ameriglide.phenix.core.Optionals;
import com.ameriglide.phenix.core.Strings;
import com.ameriglide.phenix.servlet.PhenixServlet;
import com.ameriglide.phenix.servlet.Startup;
import com.ameriglide.phenix.servlet.exception.NotFoundException;
import com.ameriglide.phenix.types.Resolution;
import com.twilio.type.PhoneNumber;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.potion.Locator;

import java.time.LocalDateTime;
import java.util.Objects;

import static com.ameriglide.phenix.core.Strings.isEmpty;
import static com.ameriglide.phenix.core.Strings.isNotEmpty;
import static com.ameriglide.phenix.types.CallDirection.*;
import static net.inetalliance.potion.Locator.$;
import static net.inetalliance.potion.Locator.$1;

@WebServlet({"/api/voice/dial", "/api/dial"})
public class VoiceDial extends PhenixServlet {

  private static final Log log = new Log();

  @Override
  protected void get(HttpServletRequest request, HttpServletResponse response) throws Exception {
    var dialingAgent = Auth.getAgent(request);

    var agent = request.getParameter("agent");
    var number = request.getParameter("number");
    var transfer = request.getParameter("transfer");
    CNAM.CallerIdSource cid;
    log.debug(
      () -> "%s: /api/dial agent=%s, number=%s, transfer=%s".formatted(dialingAgent.getFullName(), agent, number,
        transfer));

    Party called;
    if (isEmpty(agent)) {
      if (isEmpty(number)) {
        throw new IllegalArgumentException();
      }
      called = new Party(new PhoneNumber(number));
    } else {
      called = new Party($(new Agent(Integer.parseInt(agent))));
    }
    if (Strings.isEmpty(transfer)) {
      // create new Call with twilio
      var from = new Party(Auth.getAgent(request)).asSip();
      var lead = request.getParameter("lead");

      var call = new Call();
      call.setAgent(dialingAgent);
      call.setDirection(called.isAgent() ? INTERNAL:OUTBOUND);
      call.setCreated(LocalDateTime.now());
      call.setResolution(Resolution.ACTIVE);
      if (isNotEmpty(lead) && !"new".equals(lead)) {
        var opp = $(new Opportunity(Integer.valueOf(lead)));
        if (opp==null) {
          throw new NotFoundException();
        }
        call.setOpportunity(opp);
        call.setBusiness(opp.getBusiness());
        cid = Optionals
          .of($1(SkillQueue.withProduct(opp.getProductLine()).and(SkillQueue.withBusiness(opp.getBusiness()))))
          .stream()
          .filter(Objects::nonNull)
          .peek(call::setQueue)
          .map(q -> $1(VerifiedCallerId.withQueue(q)))
          .filter(Objects::nonNull)
          .findFirst()
          .orElseGet(() -> $1(VerifiedCallerId.isDefault));
      } else if (called.isAgent()) {
        cid = dialingAgent;
      } else {
        cid = $1(VerifiedCallerId.isDefault);
      }
      cid.setPhoneNumber(call);
      call.sid = Startup.router
        .call(new PhoneNumber(cid.getPhoneNumber()), from, "/voice/dial", request.getQueryString())
        .getSid();
      Locator.create("VoiceDial", call);
      log.info(() -> "New API dial %s %s -> %s".formatted(call.sid, dialingAgent.getSipUser(), called.endpoint()));
      response.sendError(HttpServletResponse.SC_NO_CONTENT);
    } else {
      var call = Locator.$(new Call(transfer));
      if (call==null) {
        log.error(() -> "Could not transfer unknown call %s".formatted(transfer));
        throw new NotFoundException();
      }
      if (called.isAgent() && call.getDirection()==QUEUE) {
        log.info(() -> "Cold transfer %s %s->%s ".formatted(transfer, dialingAgent.getFullName(),
          called.agent().getFullName()));
        Startup.router.swapTaskAgent(call.sid, call.getActiveAgent(), called.agent());

      } else {
        log.info(
          () -> "Transfering call %s %s->%s ".formatted(transfer, dialingAgent.getFullName(), called.endpoint()));
        var leg = call.getActiveLeg();

        var transferSid = switch (call.getDirection()) {
          case INTERNAL -> dialingAgent.equals(call.getAgent()) ? call.sid:leg.sid;
          case OUTBOUND -> leg.sid;
          default -> call.sid;
        };

        Startup.router.transfer(transferSid, called);
      }
    }
  }
}
