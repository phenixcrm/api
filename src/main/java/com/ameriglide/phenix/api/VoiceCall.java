package com.ameriglide.phenix.api;

import com.ameriglide.phenix.common.Agent;
import com.ameriglide.phenix.common.VerifiedCallerId;
import com.ameriglide.phenix.model.PhenixServlet;
import com.twilio.twiml.VoiceResponse;
import com.twilio.twiml.voice.Dial;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.log.Log;
import net.inetalliance.potion.Locator;

import java.util.regex.Pattern;

@WebServlet("/twilio/voice/call")
public class VoiceCall extends PhenixServlet {
  private static final Pattern sip = Pattern.compile("sip:([A-Za-z\\d.]*)@([a-z]*).sip.twilio.com");

  @Override
  protected void get(HttpServletRequest request, HttpServletResponse response) throws Exception {
    request.getParameterMap().forEach((k, v) -> log.info("%s: %s", k, String.join(", ", v)));
    var called = getSipUser(request.getParameter("Called"));
    var caller = getSipUser(request.getParameter("Caller"));
    var agent = Locator.$1(Agent.withSipUser(caller));
    log.info("%s dialed %s", agent.getFullName(), called);
    var vCid = Locator.$1(VerifiedCallerId.isDefault);
    respond(response, new VoiceResponse.Builder()
      .dial(new Dial.Builder()
        .number(called)
        .callerId(vCid.getPhoneNumber())
        .build())
      .build());
  }

  private static String getSipUser(String sipUri) {
    var matcher = sip.matcher(sipUri);
    if (matcher.matches()) {
      return matcher.group(1);
    }
    throw new IllegalArgumentException();
  }

  private final static Log log = Log.getInstance(VoiceCall.class);
}
