package com.ameriglide.phenix;

import com.ameriglide.phenix.common.Agent;
import com.ameriglide.phenix.common.JumpcloudOrg;
import com.ameriglide.phenix.common.Team;
import com.ameriglide.phenix.common.Ticket;
import com.ameriglide.phenix.core.Log;
import com.ameriglide.phenix.core.Optionals;
import com.ameriglide.phenix.servlet.Startup;
import com.ameriglide.phenix.servlet.exception.NotFoundException;
import com.ameriglide.phenix.servlet.exception.UnauthorizedException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import net.inetalliance.potion.Locator;
import net.inetalliance.potion.info.Info;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonList;
import net.inetalliance.types.www.ContentType;

import javax.naming.AuthenticationException;
import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.InitialDirContext;
import java.io.IOException;
import java.util.Hashtable;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.ameriglide.phenix.core.Strings.isEmpty;
import static jakarta.servlet.http.HttpServletResponse.*;
import static java.lang.String.format;
import static javax.naming.Context.*;
import static net.inetalliance.potion.Locator.$;

@WebServlet({"/api/login", "/api/logout", "/login", "/logout"})
public class Auth extends HttpServlet {

  private static final Log log = new Log();
  private static final Pattern logout = Pattern.compile("(.*)/logout");
  private static final Pattern email = Pattern.compile("(.*)@(.*)");
  private static final Pattern sudo = Pattern.compile("(.*):(.*)");

  public Auth() {
    super();
  }

  public static boolean isTeamLeader(final HttpServletRequest req) {
    var agent = getAgent(req);
    if (agent.isSuperUser()) {
      return true;
    }
    return Locator.count(Team.withManager(agent)) > 0;
  }

  public static Agent getAgent(HttpServletRequest request) {
    var ticket = getTicket(request);
    if (ticket==null) {
      throw new UnauthorizedException();
    }
    var agent = ticket.agent();
    if (agent==null) {
      throw new NotFoundException();
    }
    return agent;
  }

  public static Ticket getTicket(final HttpServletRequest request) {
    return (Ticket) request.getSession().getAttribute("ticket");
  }

  @Override
  public void init() throws ServletException {
    super.init();
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    Optionals.of(getTicket(request)).ifPresent(t -> {
      try {
        respond(response, toJson(t.agent()));
      } catch (ServletException e) {
        throw new RuntimeException(e);
      }
    });
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    log.trace(() -> "POST start");
    final Matcher matcher = logout.matcher(request.getRequestURI());
    if (matcher.matches()) {
      final HttpSession session = request.getSession();
      log.debug(() -> "logging out %s".formatted(session.getId()));
      session.removeAttribute("authorized");
      session.invalidate();
      final Cookie cookie = new Cookie("authToken", "");
      cookie.setMaxAge(0);
      response.addCookie(cookie);
    } else {
      final String name = request.getParameter("username").toLowerCase();
      var m = email.matcher(name);
      var timeZone = TimeZone.getTimeZone(request.getParameter("timeZone"));
      if (m.matches()) {
        var principal = m.group(1);
        var domain = m.group(2);
        final String password = request.getParameter("password");
        var sudoMatcher = sudo.matcher(password);
        if (sudoMatcher.matches()) {
          var sudoUser = sudoMatcher.group(1);
          var sudoTicket = login(sudoUser, domain, sudoMatcher.group(2), timeZone);
          if (sudoTicket==null) {
            log.error(() -> "invalid sudo login %s for %s".formatted(sudoUser, principal));
            response.sendError(SC_FORBIDDEN, "Access Denied");
          } else {
            if (sudoTicket.agent().isSuperUser()) {
              var principalTicket = new Ticket(principal, domain, timeZone);
              setTicket(request, principalTicket);
              respond(response, toJson(principalTicket.agent()));
            } else {
              log.error(() -> "forbidden sudo attempt %s for %s".formatted(sudoUser, principal));
              response.sendError(SC_FORBIDDEN, "Go Away");
            }
          }
        }
        final HttpSession session = request.getSession();
        synchronized (session) {
          var ticket = login(principal, domain, password, timeZone);
          if (ticket==null) {
            log.info(() -> "password login failed for %s".formatted(session.getId()));
            response.sendError(SC_FORBIDDEN, "Access Denied");
          } else {
            setTicket(request, ticket);
            respond(response, toJson(ticket.agent()));
          }
        }
      } else {
        response.sendError(SC_NOT_FOUND, "Please use your full email address");
      }
    }
  }

  private Ticket login(final String principal, final String domain, final String password, TimeZone timeZone) {
    if (Startup.isDevelopment()) {
      var agent = Ticket.forEmail(principal,domain);
      if(agent == null) {
        return null;
      }
      return new Ticket(agent,timeZone);
    }
    if (isEmpty(password)) {
      return null;
    }
    var jumpcloudOrg = Locator.$1(JumpcloudOrg.withDomain(domain));
    if (jumpcloudOrg==null) {
      return null;
    }
    final var env = new Hashtable<>(4);
    env.put(INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
    env.put(PROVIDER_URL, "ldaps://ldap.jumpcloud.com:636");
    env.put(SECURITY_PRINCIPAL, format("uid=%s,ou=Users,o=%s,dc=jumpcloud,dc=com", principal, jumpcloudOrg.getOrgId()));
    env.put(Context.SECURITY_CREDENTIALS, password);
    try {
      var ctx = new InitialDirContext(env);
      try {
        return new Ticket(principal, domain, timeZone);
      } finally {
        ctx.close();
      }
    } catch (CommunicationException comEx) {
      log.warn(() -> "Directory not reachable", comEx);
    } catch (AuthenticationException authEx) {
      log.warn(() -> "Password incorrect", authEx);
    } catch (NamingException nameEx) {
      log.warn(() -> "Naming error", nameEx);
    }
    return null;

  }

  private void setTicket(final HttpServletRequest request, final Ticket ticket) {
    request.getSession().setAttribute("ticket", ticket);
  }

  private void respond(HttpServletResponse res, Json content) throws ServletException {
    try (var w = res.getWriter()) {
      res.setContentType(ContentType.JSON.toString());
      w.println(Json.pretty(content));
      res.setStatus(SC_OK);
    } catch (IOException e) {
      try {
        res.sendError(500, e.getMessage());
      } catch (IOException ex) {
        log.error(ex);
      }
      throw new ServletException(e);
    }
  }

  protected Json toJson(final Agent authorized) {
    var a = Locator.$1(Agent.withEmail(authorized.getEmail()));
    if (a==null) {
      throw new IllegalStateException();
    }
    var manager = isManager(a);
    var json = Info.$(Agent.class).toJson($(a)).$("sipSecret", Startup.router.getSipSecret(a));
    var roles = new JsonList();
    json.$("roles", roles);
    var superUser = a.isSuperUser();

    if (superUser) {
      roles.add("superuser");
    }

    if (manager || superUser) {
      roles.add("manager");
      roles.add("reports");
    }
    json.$("twilioApi", "https://api.twilio.com/2010-04-01/Accounts/%s".formatted(Startup.router.accountSid));
    return json;
  }

  public static boolean isManager(final Agent a) {
    return a.isSuperUser() || Locator.count(Team.withManager(a)) > 0;
  }
}
