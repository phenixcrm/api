package com.ameriglide.phenix.twilio;

import com.twilio.twiml.TwiML;
import com.twilio.twiml.VoiceResponse;
import com.twilio.twiml.voice.Redirect;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.log.Log;

import static com.twilio.http.HttpMethod.GET;

@WebServlet("/twilio/voice/postDial")
public class PostDial extends TwiMLServlet {
  private static final VoiceResponse toVoicemail = new VoiceResponse.Builder()
    .redirect(new Redirect.Builder("/twilio/voicemail")
      .method(GET)
      .build())
    .build();

  @Override
  protected TwiML getResponse(HttpServletRequest request, HttpServletResponse response) throws Exception {
    if ("no-answer".equals(request.getParameter("DialCallStatus"))) {
      log.info("Redirecting %s to voicemail", request.getParameter("CallSid"));
      return toVoicemail;
    }
    return null;

  }

  private static final Log log = Log.getInstance(PostDial.class);
}
