package com.ameriglide.phenix;

import com.ameriglide.phenix.common.Agent;
import com.ameriglide.phenix.common.JumpcloudOrg;
import com.ameriglide.phenix.common.Team;
import com.ameriglide.phenix.common.Ticket;
import com.ameriglide.phenix.servlet.exception.NotFoundException;
import com.ameriglide.phenix.servlet.exception.UnauthorizedException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import net.inetalliance.funky.Funky;
import net.inetalliance.log.Log;
import net.inetalliance.potion.Locator;
import net.inetalliance.potion.info.Info;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonList;
import net.inetalliance.types.json.JsonString;
import net.inetalliance.types.www.ContentType;

import javax.naming.AuthenticationException;
import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.InitialDirContext;
import java.io.IOException;
import java.util.Hashtable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static jakarta.servlet.http.HttpServletResponse.*;
import static java.lang.String.format;
import static javax.naming.Context.*;
import static net.inetalliance.funky.StringFun.isEmpty;
import static net.inetalliance.log.Log.getInstance;
import static net.inetalliance.potion.Locator.$;

@WebServlet({"/login", "/logout"})
public class Auth extends HttpServlet {

  private static final transient Log log = getInstance(Auth.class);
  public Auth() {
    super();
  }

  public static Agent getAgent(HttpServletRequest request) {
    var ticket = getTicket(request);
    if(ticket == null) {
      throw new UnauthorizedException();
    }
    var agent = ticket.agent();
    if(agent == null) {
      throw new NotFoundException();
    }
    return agent;
  }

  protected Json toJson(final Agent authorized) {
    var a = Locator.$1(Agent.withEmail(authorized.getEmail()));
    if (a == null) {
      throw new IllegalStateException();
    }
    var manager = a.isSuperUser() || Locator.count(Team.withManager(a))>0;
    var json  = Info.$(Agent.class).toJson($(a))
      .$("sipSecret", Startup.router.getSipSecret(a));
    if(manager) {
      json.$("roles", JsonList.collect(Stream.of(new JsonString("manager"))));
    }
    return json;
  }

  public static boolean isTeamLeader(final HttpServletRequest req) {
    var agent =  getAgent(req);
    if(agent.isSuperUser()) {
      return true;
    }
    return Locator.count(Team.withManager(agent))>0;
  }

  private static final Pattern logout = Pattern.compile("(.*)/logout");

  @Override
  public void init() throws ServletException {
    super.init();
  }


  public static Ticket getTicket(final HttpServletRequest request) {
    return (Ticket) request.getSession().getAttribute("ticket");
  }


  private Ticket login(final String principal, final String domain, final String password) {
    if(Startup.isDevelopment()) {
      return Ticket.$(principal,domain);
    }
    if (isEmpty(password))
      return null;
    var jumpcloudOrg = Locator.$1(JumpcloudOrg.withDomain(domain));
    if(jumpcloudOrg == null) {
      return null;
    }
    final var env = new Hashtable<>(4);
    env.put(INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
    env.put(PROVIDER_URL, "ldaps://ldap.jumpcloud.com:636");
    env.put(SECURITY_PRINCIPAL, format("uid=%s,ou=Users,o=%s,dc=jumpcloud,dc=com",
      principal, jumpcloudOrg.getOrgId()));
    env.put(Context.SECURITY_CREDENTIALS, password);
    try {
      var ctx = new InitialDirContext(env);
      try {
        return Ticket.$(principal, domain);
      } finally {
        ctx.close();
      }
    } catch (CommunicationException comEx) {
      log.warning("Directory not reachable", comEx);
    } catch (AuthenticationException authEx) {
      log.warning("Password incorrect", authEx);
    } catch (NamingException nameEx) {
      log.warning("Naming error", nameEx);
    }
    return null;

  }

  private void setTicket(final HttpServletRequest request, final Ticket ticket) {
    request.getSession().setAttribute("ticket", ticket);
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    Funky.of(getTicket(request)).ifPresent(t -> {
      try {
        respond(response, toJson(t.agent()));
      } catch (ServletException e) {
        throw new RuntimeException(e);
      }
    });
  }

  private static Pattern email = Pattern.compile("(.*)@(.*)");
  private static Pattern sudo = Pattern.compile("(.*):(.*)");

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    log.trace("POST start");
    final Matcher matcher = logout.matcher(request.getRequestURI());
    if (matcher.matches()) {
      final HttpSession session = request.getSession();
      log.debug("logging out %s", session.getId());
      session.removeAttribute("authorized");
      session.invalidate();
      final Cookie cookie = new Cookie("authToken", "");
      cookie.setMaxAge(0);
      response.addCookie(cookie);
    } else {
      final String name = request.getParameter("username").toLowerCase();
      var m = email.matcher(name);
      if (m.matches()) {
        var principal = m.group(1);
        var domain = m.group(2);
        final String password = request.getParameter("password");
        var sudoMatcher = sudo.matcher(password);
        if(sudoMatcher.matches()) {
          var sudoUser = sudoMatcher.group(1);
          var sudoTicket = login(sudoUser,domain,sudoMatcher.group(2));
          if(sudoTicket == null) {
            log.error("invalid sudo login %s for %s", sudoUser, principal);
            response.sendError(SC_FORBIDDEN, "Access Denied");
          } else {
            if (sudoTicket.agent().isSuperUser()) {
              var principalTicket = Ticket.$(principal, domain);
              setTicket(request, principalTicket);
              respond(response, toJson(principalTicket.agent()));
            } else {
              log.error("forbidden sudo attempt %s for %s", sudoUser, principal);
              response.sendError(SC_FORBIDDEN, "Go Away");
            }
          }
        }
        final HttpSession session = request.getSession();
        synchronized (session) {
          var ticket = login(principal, domain, password);
          if (ticket == null) {
            log.info("password login failed for %s", session.getId());
            response.sendError(SC_FORBIDDEN, "Access Denied");
          } else {
            setTicket(request,ticket);
            respond(response, toJson(ticket.agent()));
          }
        }
      } else {
        response.sendError(SC_NOT_FOUND, "Please use your full email address");
      }
    }
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
}
