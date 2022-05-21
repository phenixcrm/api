package com.ameriglide.phenix.model;

import com.ameriglide.phenix.common.Agent;
import com.ameriglide.phenix.common.Business;
import com.ameriglide.phenix.common.ProductLine;
import com.ameriglide.phenix.common.Source;
import com.ameriglide.phenix.exception.BadRequestException;
import com.ameriglide.phenix.exception.MethodNotAllowedException;
import com.ameriglide.phenix.exception.NotFoundException;
import com.ameriglide.phenix.exception.PhenixServletException;
import com.twilio.twiml.TwiML;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.funky.ClassFun;
import net.inetalliance.log.Log;
import net.inetalliance.potion.Locator;
import net.inetalliance.types.json.Formatter;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;
import net.inetalliance.types.www.ContentType;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static java.util.Collections.emptyList;
import static net.inetalliance.funky.StringFun.isEmpty;
import static net.inetalliance.funky.StringFun.isNotEmpty;
import static net.inetalliance.types.www.ContentType.JSON;

public class PhenixServlet
  extends HttpServlet {

  public static final String contentType = JSON.toString();
  private static final Log log = Log.getInstance(PhenixServlet.class);

  protected static <T> List<T> getParameterValues(final HttpServletRequest request,
                                                  final Class<T> type,
                                                  final String name) {
    final var values = request.getParameterValues(name);
    if (values == null || values.length == 0) {
      return emptyList();
    }
    final var list = new ArrayList<T>(values.length);
    for (var value : values) {
      if (isNotEmpty(value)) {
        try {
          list.add(ClassFun.convert(type, value));
        } catch (Exception e) {
          throw new BadRequestException("Could not coerce value \"%s\" into type \"\"", value,
            type.getSimpleName());
        }
      }
    }
    return list;
  }

  public static int getParameter(final HttpServletRequest request, final String name, final int defaultValue) {
    var raw = request.getParameter(name);
    try {
      return isEmpty(raw) ? defaultValue : Integer.parseInt(raw);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  public static boolean getParameter(final HttpServletRequest request, final String name, final boolean defaultValue) {
    var raw = request.getParameter(name);
    return isEmpty(raw) ? defaultValue : Boolean.parseBoolean(raw);
  }

  public static <E extends Enum<E>> E getParameter(final HttpServletRequest request, final Class<E> type,
                                                   final String name) {
    return getParameter(request, type, name, null);
  }

  public static <E extends Enum<E>> E getParameter(final HttpServletRequest request, final Class<E> type,
                                                   final String name, final E defaultValue) {
    var parameter = request.getParameter(name);
    return isEmpty(parameter) ? defaultValue : Enum.valueOf(type, parameter.toUpperCase());
  }

  protected static void respond(final HttpServletResponse response, final Json json)
    throws IOException {
    if (json == null) {
      response.setStatus(SC_NOT_FOUND);
    } else {
      response.setHeader("Expires", "-1");
      response.setContentType(contentType);
      try (final PrintWriter writer = response.getWriter()) {
        json.format(new Formatter(writer));
        writer.flush();
      }
    }
  }

  protected static void respond(final HttpServletResponse response, final TwiML twiml) throws IOException {
    if(twiml == null) {
      response.setStatus(SC_NOT_FOUND);
    } else {
      response.setContentType("text/xml");
      try(var writer = response.getWriter()) {
        writer.write(twiml.toXml());
        writer.flush();
      }
    }
  }
  void unsupported(HttpServletRequest request) {
    log.warning("%s: %s not supported)",request.getRequestURI(),request.getMethod());
    throw new MethodNotAllowedException();
  }

  @Override
  protected final void doGet(final HttpServletRequest request, final HttpServletResponse response)
    throws ServletException, IOException {
    try {
      get(request, response);
    } catch (PhenixServletException e) {
      response.setStatus(e.status);
      addErrorBody(response, e);
    } catch (Exception e) {
      throw new ServletException(e);
    }
  }

  protected void get(final HttpServletRequest request, final HttpServletResponse response)
    throws Exception {
    unsupported(request);
  }


  private void addErrorBody(final HttpServletResponse response, final PhenixServletException e)
    throws IOException {
    final String message = e.getMessage();
    if (message != null) {
      log.error("%s: %s", e.getClass().getSimpleName(), message);
      response.setContentLength(message.length());
      response.setContentType(ContentType.TEXT.value);
      response.getWriter().println(message);
    }
  }

  @Override
  protected final void doPost(final HttpServletRequest request, final HttpServletResponse response)
    throws ServletException, IOException {
    try {
      post(request, response);
    } catch (PhenixServletException e) {
      response.setStatus(e.status);
      addErrorBody(response, e);
    } catch (Exception e) {
      throw new ServletException(e);
    }
  }

  protected void post(final HttpServletRequest request, final HttpServletResponse response)
    throws Exception {
    unsupported(request);
  }

  @Override
  protected final void doPut(final HttpServletRequest request, final HttpServletResponse response)
    throws ServletException, IOException {
    try {
      put(request, response);
    } catch (PhenixServletException e) {
      response.setStatus(e.status);
      addErrorBody(response, e);
    } catch (Exception e) {
      throw new ServletException(e);
    }
  }

  @Override
  protected final void doDelete(final HttpServletRequest request,
                                final HttpServletResponse response)
    throws ServletException, IOException {
    try {
      delete(request, response);
    } catch (PhenixServletException e) {
      response.setStatus(e.status);
      addErrorBody(response, e);
    } catch (Exception e) {
      throw new ServletException(e);
    }
  }

  protected void delete(final HttpServletRequest request, final HttpServletResponse response)
    throws Exception {
    unsupported(request);
  }

  protected void put(final HttpServletRequest request, final HttpServletResponse response)
    throws Exception {
    unsupported(request);
  }

  public Inet4Address getIpAddress(final HttpServletRequest request) {
    final String forwardedFor = request.getHeader("x-forwarded-for");
    try {
      return (Inet4Address) InetAddress
        .getByName(forwardedFor == null ? request.getRemoteAddr() : forwardedFor);
    } catch (UnknownHostException e) {
      throw new RuntimeException(e);
    }
  }

  public static JsonMap getFilters(final HttpServletRequest request) {
    final JsonMap filters = new JsonMap();
    final String[] ss = request.getParameterValues("s");
    if (ss != null && ss.length > 0) {
      final JsonMap labels = new JsonMap();
      for (final String s : ss) {
        final Business business = Locator.$(new Business(Integer.valueOf(s)));
        if (business == null) {
          throw new NotFoundException("Could not find business with id %s", s);
        }
        labels.put(s, business.getAbbreviation());
      }
      filters.put("s", labels);
    }
    final String[] sources = request.getParameterValues("src");
    if (sources != null && sources.length > 0) {
      final JsonMap labels = new JsonMap();
      for (final String sourceKey : sources) {
        final Source source = Source.valueOf(sourceKey);
        labels.put(sourceKey, source.name());
      }
      filters.put("src", labels);
    }
    final String[] pls = request.getParameterValues("pl");
    if (pls != null && pls.length > 0) {
      final JsonMap labels = new JsonMap();
      for (final String pl : pls) {
        final ProductLine productLine = Locator.$(new ProductLine(Integer.valueOf(pl)));
        if (productLine == null) {
          throw new NotFoundException("Could not find product line with id %s", pl);
        }
        labels.put(pl, productLine.getName());
      }
      filters.put("pl", labels);
    }
    final String[] as = request.getParameterValues("a");
    if (as != null && as.length > 0) {
      final JsonMap labels = new JsonMap();
      for (final String a : as) {
        final Agent agent = Locator.$(new Agent(Integer.parseInt(a)));
        if (agent == null) {
          throw new NotFoundException("Could not find agent with key %s", a);
        }
        labels.put(a, agent.getFirstNameLastInitial());
      }
      filters.put("a", labels);
    }
    return filters;

  }
}
