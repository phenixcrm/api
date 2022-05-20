package com.ameriglide.phenix.api;

import com.ameriglide.phenix.common.Agent;
import com.ameriglide.phenix.common.Call;
import com.ameriglide.phenix.common.Contact;
import com.ameriglide.phenix.common.VerifiedCallerId;
import com.ameriglide.phenix.model.PhenixServlet;
import com.ameriglide.phenix.types.Resolution;
import com.twilio.twiml.VoiceResponse;
import com.twilio.twiml.voice.Dial;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.log.Log;
import net.inetalliance.potion.Locator;

import java.time.LocalDateTime;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static com.ameriglide.phenix.types.CallDirection.OUTBOUND;

@WebServlet("/twilio/voice/call")
public class VoiceCall extends PhenixServlet {
  private static final Pattern sip = Pattern.compile("sip:([A-Za-z\\d.]*)@([a-z]*).sip.twilio.com");
  private static final Predicate<String> isAgent = Pattern.compile("[A-Za-z.]*").asMatchPredicate();


  @Override
  protected void get(HttpServletRequest request, HttpServletResponse response) throws Exception {
    var s = new StringBuilder("/twilio/voice/call:");
    request.getParameterMap().forEach((k, v) -> s.append("%n%s: %s".formatted(k, String.join(", ", v))));
    log.info(s);
    var callSid = request.getParameter("CallSid");
    var called = getSipUser(request.getParameter("Called"));
    var caller = getSipUser(request.getParameter("Caller"));
    if (isAgent.test(caller) && "inbound".equals(request.getParameter("Direction"))) { // inbound from twilio =
      // OUTBOUND call
      var agent = Locator.$1(Agent.withSipUser(caller));
      var vCid = Locator.$1(VerifiedCallerId.isDefault);
      respond(response, new VoiceResponse.Builder()
        .dial(new Dial.Builder()
          .number(called)
          .callerId(vCid.getPhoneNumber())
          .build())
        .build());
      var call = new Call(callSid);
      call.setResolution(Resolution.ACTIVE);
      call.setDirection(OUTBOUND);
      call.setCreated(LocalDateTime.now());
      call.setAgent(agent);
      call.setContact(Locator.$1(Contact.withPhoneNumber(called)));
      Locator.create("VoiceCall", call);
      log.info("Outbound %s -> %s", agent.getFullName(), called);
    }

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
