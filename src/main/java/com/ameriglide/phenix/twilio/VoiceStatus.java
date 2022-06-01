package com.ameriglide.phenix.twilio;

import com.ameriglide.phenix.common.Call;
import com.ameriglide.phenix.common.Leg;
import com.ameriglide.phenix.exception.NotFoundException;
import com.twilio.twiml.TwiML;
import com.twilio.twiml.voice.Number;
import com.twilio.twiml.voice.Sip;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.funky.StringFun;
import net.inetalliance.potion.Locator;

import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.ameriglide.phenix.types.Resolution.ANSWERED;
import static com.ameriglide.phenix.types.Resolution.DROPPED;
import static com.twilio.http.HttpMethod.GET;
import static com.twilio.twiml.voice.Number.Event.COMPLETED;
import static java.time.LocalDateTime.now;
import static net.inetalliance.funky.Funky.of;
import static net.inetalliance.potion.Locator.*;

@WebServlet("/twilio/voice/status")
public class VoiceStatus extends TwiMLServlet {
  protected TwiML getResponse(HttpServletRequest request, HttpServletResponse response) {
    if (StringFun.isEmpty(request.getParameter("ParentCallSid"))) {
      // we are operating on the primary call
      var call = Locator.$(new Call(request.getParameter("CallSid")));
      if (call == null) {
        throw new NotFoundException();
      }
      var seg = call.getActiveLeg();
      if ("inbound".equals(request.getParameter("Direction"))) {
        if (seg != null) {
          update(call, "VoiceStatus", callCopy -> {
            processCallStatusChange(request, call, seg, callCopy);
          });
        }
      }

    } else {
      // we have an update on a leg
      var call = Locator.$(new Call(request.getParameter("ParentCallSid")));
      var legSid = request.getParameter("CallSid");
      var segment = of($(new Leg(call, legSid)))
        .orElseGet(() -> {
          var leg = new Leg(call, legSid);
          leg.setCreated(now());
          switch(call.getDirection()) {
            case INBOUND -> {

            }
            case OUTBOUND -> {
              if ("outbound-dial".equals(request.getParameter("Direction"))) {
                leg.setAgent(call.getAgent());
                asParty(request,"Called").setCNAM(leg);
              }
            }
            case INTERNAL -> leg.setAgent(asParty(request, "To").agent());

          }
          create("VoiceStatus", leg);
          return leg;
        });

      update(call, "VoiceStatus", callCopy -> {
        processCallStatusChange(request, call, segment, callCopy);
      });
    }
    return null;
  }

  protected static Number buildNumber(Party party) {
    return new Number.Builder(party.endpoint())
      .statusCallbackMethod(GET)
      .statusCallbackEvents(List.of(Number.Event.ANSWERED, COMPLETED))
      .statusCallback("/twilio/voice/status")
      .build();
  }

  protected static Sip buildSip(Party party) {
    return new Sip.Builder(party.sip())
      .statusCallbackMethod(GET)
      .statusCallbackEvents(List.of(com.twilio.twiml.voice.Sip.Event.ANSWERED,
        com.twilio.twiml.voice.Sip.Event.COMPLETED))
      .statusCallback("/twilio/voice/status")
      .build();

  }

  private void processCallStatusChange(HttpServletRequest request, Call call, Leg leg, Call callCopy) {
    switch (request.getParameter("CallStatus")) {
      case "completed" -> {
        update(leg, "VoiceStatus", segmentCopy -> {
          segmentCopy.setEnded(now());
          if (leg.isAnswered()) {
            callCopy.setTalkTime(of(call.getTalkTime()).orElse(0L) + leg.getTalkTime());
            callCopy.setResolution(ANSWERED);
            info("%s was answered",call.sid);
          }
        });
        if (call.getResolution() == null) {
          callCopy.setResolution(DROPPED);
          info("%s was dropped",call.sid);
        }
        callCopy.setDuration(ChronoUnit.SECONDS.between(call.getCreated(), leg.getEnded()));
      }
      case "in-progress", "answered" -> update(leg, "VoiceStatus", segmentCopy -> {
        segmentCopy.setAnswered(now());
        info("%s was answered",call.sid);
      });
      case "no-answer", "busy", "failed" -> update(leg, "VoiceStatus", legCopy -> {
        legCopy.setEnded(now());
        callCopy.setDuration(ChronoUnit.SECONDS.between(call.getCreated(), legCopy.getEnded()));
        callCopy.setResolution(DROPPED);
      });
      default -> {
        info("%s had state %s", call.sid, request.getParameter("CallStatus"));
        callCopy.setDuration(ChronoUnit.SECONDS.between(call.getCreated(), leg.getEnded()));
        callCopy.setResolution(DROPPED);
      }
    }
  }
}
