package com.ameriglide.phenix.api;

import com.ameriglide.phenix.common.Call;
import com.ameriglide.phenix.common.Segment;
import com.ameriglide.phenix.model.PhenixServlet;
import com.ameriglide.phenix.types.Resolution;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.funky.Funky;
import net.inetalliance.log.Log;
import net.inetalliance.potion.Locator;

import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.stream.Stream;

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
    var call = Stream.of(request.getParameter("CallSid"), request.getParameter("ParentCallSid"))
      .map(sid -> Locator.$(new Call(sid)))
      .filter(Objects::nonNull)
      .findFirst()
      .orElseThrow(IllegalArgumentException::new);

    var segment = new Segment(call, Integer.parseInt(request.getParameter("SequenceNumber")));
    if (!Locator.isRead(segment)) {
      segment.setCreated(now());
      Locator.create("VoiceStatus", segment);
    }
    switch (request.getParameter("CallbackSource")) {
      case "call-progress-events" -> update(call, "VoiceStatus", callCopy -> {
        switch (request.getParameter("CallStatus")) {
          case "completed" -> {
            update(segment, "VoiceStatus", segmentCopy -> {
              segmentCopy.setEnded(now());
              if (segment.isAnswered()) {
                callCopy.setTalkTime(Funky.of(call.getTalkTime()).orElse(0L) + segment.getTalkTime());
              }
            });
            if (call.getResolution() == null) {
              callCopy.setResolution(Resolution.DROPPED);
            }
            callCopy.setDuration(ChronoUnit.SECONDS.between(call.getCreated(), segment.getEnded()));
          }
          case "answered" -> update(segment, "VoiceStatus", segmentCopy -> {
            segmentCopy.setAnswered(now());
          });
          default -> callCopy.setResolution(Resolution.DROPPED);
        }
      });
    }
  }

  private static final Log log = Log.getInstance(VoiceStatus.class);
}
