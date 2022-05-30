package com.ameriglide.phenix.twilio;

import com.ameriglide.phenix.common.Call;
import com.ameriglide.phenix.common.Contact;
import com.ameriglide.phenix.common.VerifiedCallerId;
import com.ameriglide.phenix.types.Resolution;
import com.twilio.http.HttpMethod;
import com.twilio.twiml.TwiML;
import com.twilio.twiml.VoiceResponse;
import com.twilio.twiml.voice.Number;
import com.twilio.twiml.voice.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.time.LocalDateTime;
import java.util.List;

import static com.ameriglide.phenix.types.CallDirection.INTERNAL;
import static com.ameriglide.phenix.types.CallDirection.OUTBOUND;
import static com.twilio.http.HttpMethod.GET;
import static com.twilio.twiml.voice.Number.Event.ANSWERED;
import static com.twilio.twiml.voice.Number.Event.COMPLETED;
import static net.inetalliance.potion.Locator.$1;
import static net.inetalliance.potion.Locator.create;

@WebServlet("/twilio/voice/call")
public class VoiceCall extends TwiMLServlet {


  private Number buildNumber(Party party) {
    return new Number.Builder(party.endpoint())
      .statusCallbackMethod(GET)
      .statusCallbackEvents(List.of(ANSWERED, COMPLETED))
      .statusCallback("/twilio/voice/status")
      .build();
  }
  private Sip buildSip(Party party) {
    return new Sip.Builder(party.sip()+";transport=tls")
      .statusCallbackMethod(GET)
      .statusCallbackEvents(List.of(com.twilio.twiml.voice.Sip.Event.ANSWERED,
        com.twilio.twiml.voice.Sip.Event.COMPLETED))
      .statusCallback("/twilio/voice/status")
      .build();

  }

  @Override
  protected void post(HttpServletRequest request, HttpServletResponse response) {


  }

  @Override
  protected TwiML getResponse(HttpServletRequest request, HttpServletResponse response) {
    var callSid = request.getParameter("CallSid");
    var called = asParty(request.getParameter("Called"));
    var caller = asParty(request.getParameter("Caller"));
    var call = new Call(callSid);
    call.setResolution(Resolution.ACTIVE);
    call.setCreated(LocalDateTime.now());
    try {
      if (caller.isAgent()) {
        call.setAgent(caller.agent());
        if (called.isAgent()) {
          info("%s is a new internal call %s->%s", callSid, caller, called);
          call.setDirection(INTERNAL);
          return new VoiceResponse.Builder()
            .dial(new Dial.Builder()
              .action("/twilio/voice/postDial")
              .method(HttpMethod.GET)
              .answerOnBridge(true)
              .timeout(15)
              .sip(buildSip(called))
              .build())
            .build();
        } else {
          info("%s is a new outbound call %s->%s", callSid, caller, called);
          call.setDirection(OUTBOUND);
          var vCid = $1(VerifiedCallerId.isDefault);
          call.setCallerId(called.callerId());
          call.setContact($1(Contact.withPhoneNumber(called.endpoint())));
          info("Outbound %s -> %s", caller.agent().getFullName(), called);
          return new VoiceResponse.Builder()
            .dial(new Dial.Builder()
              .number(buildNumber(called))
              .callerId(vCid.getPhoneNumber())
              .build())
            .build();
        }
      } else {
        // INBOUND or IVR/QUEUE call
        info("%s is a new inbound call %s->%s", callSid, caller, called);
        return new VoiceResponse.Builder()
          .gather(new Gather.Builder()
            .action("/twilio/menu/show")
            .numDigits(1)
            .timeout(19)
            .build())
          .say(speak("Thank you for calling AmeriGlide, your headquarters for Home Safety."))
          .pause(new Pause.Builder().length(1).build())
          .say(new Say.Builder().build())
          .build();

      }
    } finally {
      create("VoiceCall", call);
    }

  }

}
