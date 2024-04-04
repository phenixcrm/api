package com.ameriglide.phenix.api;

import com.ameriglide.phenix.Auth;
import com.ameriglide.phenix.DialMode;
import com.ameriglide.phenix.common.*;
import com.ameriglide.phenix.core.Log;
import com.ameriglide.phenix.core.Optionals;
import com.ameriglide.phenix.servlet.PhenixServlet;
import com.ameriglide.phenix.servlet.exception.NotFoundException;
import com.ameriglide.phenix.types.Resolution;
import com.ameriglide.phenix.types.WorkerState;
import com.twilio.type.PhoneNumber;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.potion.Locator;
import net.inetalliance.sql.OrderBy;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.ameriglide.phenix.core.Strings.isNotEmpty;
import static com.ameriglide.phenix.servlet.Startup.router;
import static com.ameriglide.phenix.types.CallDirection.*;
import static net.inetalliance.potion.Locator.$;
import static net.inetalliance.potion.Locator.$1;

@WebServlet({"/api/voice/dial", "/api/dial"})
public class VoiceDial extends PhenixServlet {

  private static final Log log = new Log();

  @Override
  protected void get(HttpServletRequest request, HttpServletResponse response) throws Exception {
    var dialingAgent = Auth.getAgent(request);

    var param = DialMode.fromRequest(request);
    var transfer = request.getParameter("transfer");
    var lead = request.getParameter("lead");

    CNAM.CallerIdSource cid;
    log.debug(
      () -> "%s: /api/dial mode=%s, target=%s, transfer=%s, lead=%s".formatted(dialingAgent.getFullName(), param.mode(),
        param.value(), transfer, lead));
    var called = switch (param.mode()) {
      case NUMBER -> new Party(new PhoneNumber(param.value()));
      case AGENT -> new Party($(new Agent(Integer.parseInt(param.value()))));
      case QUEUE -> null;
    };

    if (!"true".equals(transfer)) {
      // create new Call with twilio
      var from = new Party(Auth.getAgent(request)).asSip();

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
        call.setContact(opp.getContact());
        call.setOpportunity(opp);
        call.setBusiness(opp.getBusiness());
        cid = Optionals
          .of($1(VerifiedCallerId.withProductLine(opp.getProductLine())))
          .stream()
          .peek(vCid-> call.setQueue(vCid.getQueue()))
          .findFirst()
          .orElseGet(() -> $1(VerifiedCallerId.isDefault));
      } else if (called.isAgent()) {
        cid = dialingAgent;
      } else {
        cid = $1(VerifiedCallerId.isDefault);
      }
      cid.setPhoneNumber(call);
      call.sid = "ph" + UUID.randomUUID().toString().replaceAll("-", "");
      Locator.create("VoiceDial", call);
      router.call(new PhoneNumber(cid.getPhoneNumber()), from, "/voice/dial",
        request.getQueryString() + "&call=" + call.sid);
      log.info(() -> "New API dial %s %s -> %s".formatted(call.sid, dialingAgent.getSipUser(), called.endpoint()));
      response.sendError(HttpServletResponse.SC_NO_CONTENT);
    } else {
      var voicemail = "true".equals(request.getParameter("voicemail"));
      var call = Locator.$1(
        Call.withAgent(dialingAgent).and(Call.isActiveVoiceCall).orderBy("created", OrderBy.Direction.DESCENDING));
      if (call==null) {
        log.error(() -> "Could not transfer unknown call %s".formatted(transfer));
        throw new NotFoundException();
      }
      switch (param.mode()) {
        case AGENT -> {
          if (call.getDirection()==QUEUE) {
            var calledAgent = called.agent();
            var worker = router.getWorker(calledAgent.getSid());
            if (voicemail || WorkerState.from(worker)!=WorkerState.AVAILABLE) {
              log.info(() -> "Cold transfer to VM %s %s->%s ".formatted(transfer, dialingAgent.getFullName(),
                calledAgent.getFullName()));
              Locator.update(call, "VoiceDial", copy -> {
                copy.setAgent(called.agent());
              });
              router.sendToVoicemail(call.sid, router.getPrompt(called.agent()));
            } else {
              log.info(() -> "Warm transfer %s %s->%s ".formatted(transfer, dialingAgent.getFullName(),
                called.agent().getFullName()));
              router.warmAdd(call.sid, call.getActiveAgent(), called.agent());

            }
          } else {
            log.info(
              () -> "Transfering call %s %s->%s ".formatted(transfer, dialingAgent.getFullName(), called.endpoint()));
            var leg = call.getActiveLeg();
            var transferSid = switch (call.getDirection()) {
              case INTERNAL -> dialingAgent.equals(call.getAgent()) ? call.sid:leg.sid;
              case OUTBOUND -> leg.sid;
              default -> call.sid;
            };
            router.transfer(transferSid, called);
          }
        }
        case NUMBER -> {
          log.info(() -> "Transfering call %s %s->%s ".formatted(transfer, dialingAgent.getFullName(), param.value()));
          router.transfer(call.sid, param.value());
        }
        case QUEUE -> {
          var q = $(new SkillQueue(Integer.parseInt(param.value())));
          log.info(() -> "Transfering call %s %s->%s ".formatted(transfer, dialingAgent.getFullName(), q.getName()));
          router.transfer(call.sid, q);
        }
      }
    }
  }
}
