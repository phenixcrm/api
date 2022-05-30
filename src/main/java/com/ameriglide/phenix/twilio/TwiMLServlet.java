package com.ameriglide.phenix.twilio;

import com.ameriglide.phenix.PhenixServlet;
import com.ameriglide.phenix.common.Agent;
import com.ameriglide.phenix.types.CallerId;
import com.github.tomaslanger.chalk.Chalk;
import com.twilio.twiml.TwiML;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.funky.Funky;
import net.inetalliance.log.Log;

import java.io.IOException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static jakarta.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static net.inetalliance.potion.Locator.$1;

public abstract class TwiMLServlet extends PhenixServlet {
  protected void log(final String format, Object... args) {
    if(format != null) {
      System.out.printf("%s\t%s\t%n%s%n",
        Chalk.on("TwiML").bgRed().cyan().bold(),
        Chalk.on(getClass().getSimpleName()).white().bold(),
        format.formatted(args));
    }

  }
  protected static void respond(final HttpServletResponse response, final TwiML twiml) throws IOException {
    if (twiml == null) {
      response.setStatus(SC_NOT_FOUND);
    } else {
      response.setContentType("text/xml");
      try (var writer = response.getWriter()) {
        writer.write(twiml.toXml());
        writer.flush();
      }
    }
  }

  private static final String bar = "\n==================================";
  private static final String line = "\n----------------------------------";

  @Override
  protected void get(HttpServletRequest request, HttpServletResponse response) throws Exception {
    var s = new StringBuilder(bar)
      .append("\nGET ")
      .append(request.getRequestURI())
      .append(line);
    request.getParameterMap().forEach((k, v) -> s.append("%n%s: %s".formatted(k, String.join(", ", v))));
    s.append(line);
    log(s.toString());
    var twiml = getResponse(request, response);
    if (twiml == null) {
      response.sendError(SC_NO_CONTENT);
    } else {
      respond(response, twiml);
    }
  }

  @Override
  protected void post(HttpServletRequest request, HttpServletResponse response) throws Exception {
    var s = new StringBuilder(bar)
      .append("\nPOST ")
      .append(request.getRequestURI())
      .append(line);
    request.getParameterMap().forEach((k, v) -> s.append("%n%s: %s".formatted(k, String.join(", ", v))));
    try (var reader = request.getReader()) {
      var data = new StringBuilder("\nRequest Body:\n");
      String line;
      while ((line = reader.readLine()) != null) {
        data.append(line);
      }
      s.append(data);
    }
    s.append(line);
    log.info(s);
    respond(response, postResponse(request, response));
  }

  protected TwiML postResponse(HttpServletRequest request, HttpServletResponse response) {
    return null;
  }

  protected abstract TwiML getResponse(HttpServletRequest request, HttpServletResponse response) throws Exception;

  private static final Log log = Log.getInstance(TwiMLServlet.class);
  private static Predicate<String> e164 = Pattern.compile("^\\+[1-9]\\d{1,14}$").asMatchPredicate();

  protected static Party asParty(String sipUri) {
    var matcher = sip.matcher(sipUri);
    if (matcher.matches()) {
      var user = matcher.group(1);
      return new Party(user, isAgent.test(user), sipUri);
    } else if (e164.test(sipUri)) {
      return new Party(sipUri, false, sipUri);
    }
    throw new IllegalArgumentException();
  }

  private static final Pattern sip = Pattern.compile("sip:([A-Za-z\\d.]*)@([a-z]*).sip.twilio.com");
  private static final Predicate<String> isAgent = Pattern.compile("[A-Za-z.]*").asMatchPredicate();

  private static final Function<String, Agent> lookup = Funky.memoize(32, (user) -> $1(Agent.withSipUser(user)));

  public record Party(String endpoint, boolean isAgent, String sip) {
    Agent agent() {
      if (isAgent) {
        return lookup.apply(endpoint);
      }
      throw new IllegalStateException();
    }

    CallerId callerId() {
      if (isAgent) {
        var agent = agent();
        return new CallerId(agent.getFullName(), agent.getSipUser());
      }
      return new CallerId(null, endpoint);
    }

  }

}
