package com.ameriglide.phenix.twilio;

import com.ameriglide.phenix.common.Call;
import com.ameriglide.phenix.exception.NotFoundException;
import com.twilio.http.HttpMethod;
import com.twilio.twiml.TwiML;
import com.twilio.twiml.VoiceResponse;
import com.twilio.twiml.voice.Record;
import com.twilio.twiml.voice.Say;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.funky.StringFun;
import net.inetalliance.potion.Locator;

import static com.ameriglide.phenix.types.CallDirection.INTERNAL;
import static com.ameriglide.phenix.types.Resolution.VOICEMAIL;
import static com.twilio.twiml.voice.Record.Trim.TRIM_SILENCE;
import static net.inetalliance.funky.Funky.of;
import static net.inetalliance.funky.StringFun.isNotEmpty;
import static net.inetalliance.potion.Locator.update;

@WebServlet("/twilio/voicemail")
public class Voicemail extends TwiMLServlet {
  @Override
  protected TwiML getResponse(HttpServletRequest request, HttpServletResponse response) {
    log("%s entered voicemail", request.getParameter("CallSid"));
    return new VoiceResponse.Builder()
      .say(new Say.Builder("The party you are trying to reach is not available. Please leave a message")
        .voice(Say.Voice.POLLY_AMY_NEURAL)
        .build())
      .record(new Record.Builder()
        .playBeep(true)
        .method(HttpMethod.POST)
        .action("/twilio/voicemail")
        .trim(TRIM_SILENCE)
        .transcribeCallback("/twilio/voicemail")
        .maxLength(300)
        .build())
      .build();
  }

  @Override
  protected TwiML postResponse(HttpServletRequest request, HttpServletResponse response) {
    Call call = Locator.$(new Call(request.getParameter("CallSid")));
    if (call == null) {
      throw new NotFoundException();
    }
    try {
      if ("completed".equals(request.getParameter("CallStatus"))) {
        update(call, "Voicemail", copy -> {
          copy.setResolution(VOICEMAIL);
          var duration =
            of(request.getParameter("RecordingDuration"))
              .filter(StringFun::isNotEmpty)
              .map(Long::parseLong)
              .orElse(0L);
          copy.setDuration(of(call.getDuration()).orElse(0L) + duration);
          copy.setSilent(copy.getDuration() == 0);
          copy.setTalkTime(copy.getDuration());
          copy.setVoicemailSid(request.getParameter("RecordingSid"));
          if (call.getDirection() == INTERNAL) {
            copy.setBlame(null);
          }
          of(request.getParameter("TranscriptionText"))
            .filter(StringFun::isNotEmpty)
            .ifPresent(copy::setTranscription);
          log("%s voicemail recorded%s", copy.sid, isNotEmpty(copy.getTranscription()) ? " transcribed" : "");
        });
      } else {
        log("%s voicemail changed status to %s", call.sid, request.getParameter("CallStatus"));
      }
    } catch (Throwable t) {
      error(t);
    }
    return null;
  }
}
