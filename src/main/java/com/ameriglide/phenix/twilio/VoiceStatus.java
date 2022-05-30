package com.ameriglide.phenix.twilio;

import com.ameriglide.phenix.common.Call;
import com.ameriglide.phenix.common.Leg;
import com.ameriglide.phenix.exception.NotFoundException;
import com.ameriglide.phenix.types.CallDirection;
import com.twilio.twiml.TwiML;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.funky.StringFun;
import net.inetalliance.potion.Locator;

import java.time.temporal.ChronoUnit;

import static com.ameriglide.phenix.types.Resolution.ANSWERED;
import static com.ameriglide.phenix.types.Resolution.DROPPED;
import static java.time.LocalDateTime.now;
import static net.inetalliance.funky.Funky.of;
import static net.inetalliance.potion.Locator.*;
import static net.inetalliance.potion.Locator.update;

@WebServlet("/twilio/voice/status")
public class VoiceStatus extends TwiMLServlet {
  protected TwiML getResponse(HttpServletRequest request, HttpServletResponse response) {
    if (StringFun.isEmpty(request.getParameter("ParentCallSid"))) {
      // we are operating on the primary call
      var call = Locator.$(new Call(request.getParameter("CallSid")));
      if (call == null) {
        throw new NotFoundException();
      }
      var seg = call.getActiveSegment();
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
      var segmentSid = request.getParameter("CallSid");
      var segment = of($(new Leg(call, segmentSid)))
        .orElseGet(() -> {
          var seg = new Leg(call, segmentSid);
          seg.setCreated(now());
          if (call.getDirection() == CallDirection.OUTBOUND) {
            if ("outbound-dial".equals(request.getParameter("Direction"))) {
              seg.setAgent(call.getAgent());
              seg.setCallerId(asParty(request.getParameter("Called")).callerId());
            }
          }
          create("VoiceStatus", seg);
          return seg;
        });

      update(call, "VoiceStatus", callCopy -> {
        processCallStatusChange(request, call, segment, callCopy);
      });
    }
    return null;
  }

  private void processCallStatusChange(HttpServletRequest request, Call call, Leg leg, Call callCopy) {
    switch (request.getParameter("CallStatus")) {
      case "completed" -> {
        update(leg, "VoiceStatus", segmentCopy -> {
          segmentCopy.setEnded(now());
          if (leg.isAnswered()) {
            callCopy.setTalkTime(of(call.getTalkTime()).orElse(0L) + leg.getTalkTime());
            callCopy.setResolution(ANSWERED);
            log("%s was answered",call.sid);
          }
        });
        if (call.getResolution() == null) {
          callCopy.setResolution(DROPPED);
          log("%s was dropped",call.sid);
        }
        callCopy.setDuration(ChronoUnit.SECONDS.between(call.getCreated(), leg.getEnded()));
      }
      case "in-progress", "answered" -> update(leg, "VoiceStatus", segmentCopy -> {
        segmentCopy.setAnswered(now());
        log("%s was answered",call.sid);
      });
      default -> {
        log("%s had state %s", call.sid, request.getParameter("CallStatus"));
        callCopy.setResolution(DROPPED);
      }
    }
  }
}
