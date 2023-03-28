package com.ameriglide.phenix;

import com.ameriglide.phenix.common.Agent;
import com.ameriglide.phenix.common.Ticket;
import com.ameriglide.phenix.core.Strings;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.potion.Locator;

import java.io.IOException;
import java.util.TimeZone;

import static com.ameriglide.phenix.Auth.setTicket;

@WebServlet("/api/trusted/login")
public class TrustedLogin extends HttpServlet {
  @Override
  protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws
    ServletException,
    IOException {
    var email = req.getParameter("email");
    var timeZoneName = req.getParameter("timeZone");
    if (Strings.isEmpty(email) || Strings.isEmpty(timeZoneName)) {
      resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
    }
    var timeZone = TimeZone.getTimeZone(timeZoneName);
    var agent = Locator.$1(Agent.withEmail(email));
    if (agent==null) {
      resp.sendError(HttpServletResponse.SC_NOT_FOUND);
    } else {
      setTicket(req, new Ticket(agent, timeZone));
      Auth.respond(resp, Auth.toJson(agent));
    }
  }
}
