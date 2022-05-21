package com.ameriglide.phenix.api;

import com.ameriglide.phenix.common.Agent;
import com.ameriglide.phenix.common.Call;
import com.ameriglide.phenix.common.Contact;
import com.ameriglide.phenix.common.VerifiedCallerId;
import com.ameriglide.phenix.model.PhenixServlet;
import com.ameriglide.phenix.types.CallerId;
import com.ameriglide.phenix.types.Resolution;
import com.twilio.twiml.VoiceResponse;
import com.twilio.twiml.voice.Dial;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.funky.Funky;
import net.inetalliance.log.Log;
import net.inetalliance.potion.Locator;

import java.time.LocalDateTime;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static com.ameriglide.phenix.types.CallDirection.INTERNAL;
import static com.ameriglide.phenix.types.CallDirection.OUTBOUND;
import static net.inetalliance.potion.Locator.$1;

@WebServlet("/twilio/voice/call")
public class VoiceCall extends PhenixServlet {
  private static final Pattern sip = Pattern.compile("sip:([A-Za-z\\d.]*)@([a-z]*).sip.twilio.com");
  private static final Predicate<String> isAgent = Pattern.compile("[A-Za-z.]*").asMatchPredicate();

  private static final Function<String,Agent> lookup = Funky.memoize(32,(user) -> $1(Agent.withSipUser(user)));

  record Party(String endpoint, boolean isAgent) {
    Agent agent() {
      if(isAgent) {
        return lookup.apply(endpoint);
      }
      throw new IllegalStateException();
    }
    CallerId callerId() {
      if(isAgent) {
        var agent = agent();
        return new CallerId(agent.getFullName(),agent.getSipUser());
      }
    }
  }

  @Override
  protected void get(HttpServletRequest request, HttpServletResponse response) throws Exception {
    var s = new StringBuilder("/twilio/voice/call:");
    request.getParameterMap().forEach((k, v) -> s.append("%n%s: %s".formatted(k, String.join(", ", v))));
    log.info(s);
    var callSid = request.getParameter("CallSid");
    var called = asParty(request.getParameter("Called"));
    var caller = asParty(request.getParameter("Caller"));
    var call = new Call(callSid);
    call.setResolution(Resolution.ACTIVE);
    call.setCreated(LocalDateTime.now());
    if (caller.isAgent) {
      call.setAgent(caller.agent());
      if(called.isAgent) {
        call.setDirection(INTERNAL);
        call.setCallerId(called.callerId());
      } else {
        call.setDirection(OUTBOUND);
        var vCid = $1(VerifiedCallerId.isDefault);
        respond(response, new VoiceResponse.Builder()
          .dial(new Dial.Builder()
            .number(called.endpoint())
            .callerId(vCid.getPhoneNumber())
            .build())
          .build());
        call.setContact($1(Contact.withPhoneNumber(called.endpoint())));
        log.info("Outbound %s -> %s", caller.agent().getFullName(), called);
      }
    } else {
      // INBOUND or IVR/QUEUE call
    }
    Locator.create("VoiceCall", call);

  }

  private static Party asParty(String sipUri) {
    var matcher = sip.matcher(sipUri);
    if (matcher.matches()) {
      var user = matcher.group(1);
      return new Party(user,isAgent.test(user));
    }
    throw new IllegalArgumentException();
  }

  private final static Log log = Log.getInstance(VoiceCall.class);
}
