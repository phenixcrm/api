package com.ameriglide.phenix.api;

import com.ameriglide.phenix.common.Call;
import com.ameriglide.phenix.common.Segment;
import com.ameriglide.phenix.model.PhenixServlet;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.funky.Funky;
import net.inetalliance.log.Log;
import net.inetalliance.potion.Locator;

import java.time.temporal.ChronoUnit;

import static com.ameriglide.phenix.types.Resolution.ANSWERED;
import static com.ameriglide.phenix.types.Resolution.DROPPED;
import static jakarta.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static java.lang.String.join;
import static java.time.LocalDateTime.now;
import static net.inetalliance.potion.Locator.update;

@WebServlet("/twilio/voice/status")
public class VoiceStatus extends PhenixServlet {
  @Override
  protected void get(HttpServletRequest request, HttpServletResponse response) throws Exception {
    var s = new StringBuilder("/twilio/voice/status:");
    request.getParameterMap().forEach((k, v) -> s.append("%n%s: %s".formatted(k, join(", ", v))));
    log.info(s);
    var call = Locator.$(new Call(request.getParameter("ParentCallSid")));
    var segmentSid = request.getParameter("CallSid");
    var segment = Funky.of(Locator.$(new Segment(call, segmentSid))).orElseGet(()->{
      var seg = new Segment(call,segmentSid);
      Locator.create("VoiceStatus",seg);
      return seg;
    });
    switch (request.getParameter("CallbackSource")) {
      case "call-progress-events" -> update(call, "VoiceStatus", callCopy -> {
        switch (request.getParameter("CallStatus")) {
          case "completed" -> {
            update(segment, "VoiceStatus", segmentCopy -> {
              segmentCopy.setEnded(now());
              if (segment.isAnswered()) {
                callCopy.setTalkTime(Funky.of(call.getTalkTime()).orElse(0L) + segment.getTalkTime());
                callCopy.setResolution(ANSWERED);
              }
            });
            if (call.getResolution() == null) {
              callCopy.setResolution(DROPPED);
            }
            callCopy.setDuration(ChronoUnit.SECONDS.between(call.getCreated(), segment.getEnded()));
          }
          case "in-progress", "answered" -> update(segment, "VoiceStatus", segmentCopy -> {
            segmentCopy.setAnswered(now());
          });
          default -> callCopy.setResolution(DROPPED);
        }
      });
    }
    response.sendError(SC_NO_CONTENT);
  }

  private static final Log log = Log.getInstance(VoiceStatus.class);
}
