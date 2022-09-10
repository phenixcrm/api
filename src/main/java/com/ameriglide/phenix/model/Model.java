package com.ameriglide.phenix.model;

import com.ameriglide.phenix.Auth;
import com.ameriglide.phenix.core.Classes;
import com.ameriglide.phenix.core.Log;
import com.ameriglide.phenix.core.Optionals;
import com.ameriglide.phenix.core.Strings;
import com.ameriglide.phenix.servlet.PhenixServlet;
import com.ameriglide.phenix.servlet.exception.*;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.potion.Locator;
import net.inetalliance.potion.info.Info;
import net.inetalliance.potion.info.Property;
import net.inetalliance.potion.info.SubObjectProperty;
import net.inetalliance.potion.info.UniqueKeyError;
import net.inetalliance.potion.query.Query;
import net.inetalliance.potion.query.SortedQuery;
import net.inetalliance.sql.OrderBy;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;
import net.inetalliance.validation.ValidationErrors;
import net.inetalliance.validation.Validator;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.ameriglide.phenix.core.Strings.isEmpty;
import static com.ameriglide.phenix.core.Strings.isNotEmpty;
import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static jakarta.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static java.util.Collections.singleton;
import static net.inetalliance.potion.Locator.read;
import static net.inetalliance.potion.Locator.types;
import static net.inetalliance.types.www.ContentType.MULTIPART_FORMDATA;
import static net.inetalliance.types.www.ContentType.parse;

public class Model<T>
    extends PhenixServlet {

  private static final JsonMap emptyMap = new JsonMap();
  private static final Log log = new Log();
  protected final Pattern pattern;
  private final File temporaryStorage = new File("/tmp");
  private File repository;

  public Model() {
    this(Pattern.compile(".*/model/([^/]*)/?(.*)?"));
  }

  protected Model(final Pattern pattern) {
    this.pattern = pattern;
  }

  public <K extends Key<T>> JsonMap createObject(final K key,
                                                                                    final HttpServletRequest request,
                                                                                    final HttpServletResponse response, final JsonMap data,
                                                                                    final Function<T, JsonMap> toJson) {
    return createObject(key, request, response, data, toJson, t -> {
    });
  }

  protected void setDefaults(final T t, final HttpServletRequest request, JsonMap data) {

  }

  public <K extends Key<T>> JsonMap createObject(final K key, final HttpServletRequest request,
                                                 final HttpServletResponse response, final JsonMap data,
                                                 final Function<T, JsonMap> toJson, final Consumer<? super T> success) {
    try {
      final T t = key.info.type.getDeclaredConstructor().newInstance();
      final ValidationErrors errors = new ValidationErrors();
      final JsonMap externalMap = new JsonMap();
      setDefaults(t,request,data);
      setProperties(request, data, t, errors);
      if (isNotEmpty(key.id)) {
        final Property<T, ?> keyProperty = key.info.keys().findFirst().orElseThrow();
        keyProperty.field.set(t, Classes.convert(keyProperty.type, key.id));
      }
      final Locale locale = request.getLocale();
      errors.add(Validator.create(locale, t));
      if (errors.isEmpty()) {
        try {
          final String user = getRemoteUser(request);
          Locator.create(user, t);
          if (!externalMap.isEmpty()) {
            Locator.update(t, user, copy -> {
              key.info.external().forEach(property -> property.setIf(copy, externalMap));
            });
          }
          success.accept(t);
          return toJson.apply(t);
        } catch (UniqueKeyError ue) {
          final String name = key.info.keys().findFirst().orElseThrow().field.getName();
          errors.put(name,
              singleton(Validator.messages.get(locale, "validation.uniqueKey", Validator.messages.get(locale,
                      key.info.type.getSimpleName()), name)));
          response.setStatus(SC_BAD_REQUEST);
          return errors.toJsonMap();
        }
      } else {
        response.setStatus(SC_BAD_REQUEST);
        return onError(key, data, errors.toJsonMap());
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static <T> void setProperties(final HttpServletRequest request, final JsonMap data,
      final T t,
      final ValidationErrors errors) {
    final Info<T> info = Info.$(t);
    info.properties().filter(p -> !p.isGenerated()).forEach(property -> {
      if (property instanceof SubObjectProperty && ((SubObjectProperty) property).external) {
        if (isMultipart(request)) {
          final JsonMap propertyMap = data.getMap(property.field.getName());
          final String filename = propertyMap == null ? null : propertyMap.get("file");
          final ValidationErrors uploadErrors = new ValidationErrors();  //todo: this doesn't check anything!
          if (uploadErrors.isEmpty()) {
            property.setIf(t, data);
          } else {
            errors.add(uploadErrors);
          }
        }
      } else {
        property.setIf(t, data);
      }
    });
  }

  public static String getRemoteUser(final HttpServletRequest request) {
    return Optionals.of(Auth.getTicket(request)).map(t->t.agent().getName()).orElse("");
  }

  private static boolean isMultipart(final HttpServletRequest request) {
    return parse(request.getContentType()) == MULTIPART_FORMDATA;
  }

  public static <T> ValidationErrors update(final HttpServletRequest request, final T t,
      final JsonMap data) {
    return Locator.update(t, getRemoteUser(request), new Function<>() {
      final ValidationErrors errors = new ValidationErrors();

      @Override
      public ValidationErrors apply(final T copy) {
        final JsonMap externalMap = new JsonMap();
        setProperties(request, data, copy, errors);
        final Locale locale = request.getLocale();
        errors.add(Validator.update(locale, copy));
        return errors;
      }
    });
  }

  private static Json toJson(final HttpServletResponse response, final Throwable e)
      throws IOException {
    response.setStatus(SC_INTERNAL_SERVER_ERROR);
    return Json.Factory.$(e);
  }

  public Pattern getPattern() {
    return pattern;
  }

  @Override
  public void init(final ServletConfig config)
      throws ServletException {
    super.init(config);

  }

  public Query<T> all(final Class<T> type, final HttpServletRequest request) {
    return Query.all(type);
  }

  @SuppressWarnings("unchecked")
  @Override
  protected void get(final HttpServletRequest request, final HttpServletResponse response)
      throws Exception {
    if (requireAuthentication() && !isAuthenticated(request)) {
      throw new UnauthorizedException();
    }
    try {
      final Key<T> key = getKey(request);
      log.trace(()->"Model GET %s".formatted( key));
      if (isEmpty(key.id)) {
        respond(response, getAll(request));
      } else {
        final T t = lookup(key, request);
        if (t == null) {
          throw new NotFoundException();
        } else if (isReadAuthorized(request, t)) {
          respond(response, toJson(key, t, request));
        } else {
          throw new ForbiddenException();
        }
      }
    } catch (PhenixServletException e) {
      throw e;
    } catch (Throwable t) {
      log.error(t);
      respond(response, toJson(response, t));
    }
  }

  @Override
  protected void post(final HttpServletRequest request, final HttpServletResponse response)
      throws Exception {
    if (requireAuthentication() && !isAuthenticated(request)) {
      throw new UnauthorizedException();
    }
    try {
      final Key<T> key = getKey(request);
      if (key != null) {
        log.trace(()->"Model POST %s".formatted(key));
        respond(response, create(key, request, response));
      }
    } catch (PhenixServletException e) {
      throw e;
    } catch (Throwable t) {
      log.error(t);
      respond(response, toJson(response, t));
    }
  }

  @Override
  protected void delete(final HttpServletRequest request, final HttpServletResponse response)
      throws Exception {
    if (requireAuthentication() && !isAuthenticated(request)) {
      throw new UnauthorizedException();
    }
    try {
      final Key<T> key = getKey(request);
      if (key != null) {
        if (isEmpty(key.id)) {
          throw new ForbiddenException(); // not allowing delete of all objects at this time
        } else {
          log.trace(()->"Model DELETE %s".formatted(key));
          final T t = lookup(key, request);
          if (!isDeleteAuthorized(request, t)) {
            throw new ForbiddenException();
          }
          respond(response, delete(request, lookup(key, request)));
        }
      }
    } catch (PhenixServletException e) {
      throw e;
    } catch (Throwable t) {
      log.error(t);
      respond(response, toJson(response, t));
    }
  }

  protected T lookup(final Key<T> key, final HttpServletRequest request) {
    return "new".equals(key.id) ? getDefaults(key, request) : key.info.lookup(key.id);
  }

  protected boolean isDeleteAuthorized(final HttpServletRequest request, final T t) {
    return isAuthorized(request, t);
  }

  protected Json delete(final HttpServletRequest request, final T object) {
    if (object != null) {
      Locator.delete(getRemoteUser(request), object);
    }
    return emptyMap;
  }

  protected T getDefaults(final Key<T> key, final HttpServletRequest request) {
    try {
      return key.type.getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  protected boolean isAuthorized(final HttpServletRequest request, final T t) {
    return true;
  }

  @Override
  protected void put(final HttpServletRequest request, final HttpServletResponse response)
      throws Exception {
    if (requireAuthentication() && !isAuthenticated(request)) {
      throw new UnauthorizedException();
    }
    try {
      final Key<T> key = getKey(request);
      if (key != null) {
        if (isEmpty(key.id)) {
          response.sendError(SC_BAD_REQUEST, "PUT called without an id");
        } else {
          log.trace(()->"Model PUT %s".formatted(key));
          final T t = lookup(key, request);
          if (t == null) {
            throw new NotFoundException("Cannot find %s with key %s", key.type.getSimpleName(),
                key.id);
          }
          read(t);
          if (!isUpdateAuthorized(request, t)) {
            throw new ForbiddenException();
          }
          final JsonMap data = parseData(request);
          respond(response, update(key, request, response, t, data));
        }
      }
    } catch (PhenixServletException e) {
      throw e;
    } catch (Throwable t) {
      log.error(t);
      respond(response, toJson(response, t));
    }
  }

  protected boolean isUpdateAuthorized(final HttpServletRequest request, final T t) {
    return isAuthorized(request, t);
  }

  protected Json update(final Key<T> key, final HttpServletRequest request,
      final HttpServletResponse response, final T t,
      final JsonMap data)
      throws IOException {
    final ValidationErrors errors = update(request, t, data);
    if (errors.isEmpty()) {
      return toJson(key, t, request);
    } else {
      response.setStatus(SC_BAD_REQUEST);
      return onError(key,data,errors.toJsonMap());
    }
  }
  protected JsonMap onError(Key<T> key, JsonMap data, JsonMap errors) {
    return errors;
  }

  protected boolean requireAuthentication() {
    return false;
  }

  protected boolean isAuthenticated(final HttpServletRequest request) {
    return false;
  }

  protected Key<T> getKey(final HttpServletRequest request) {
    final String uri = request.getRequestURI();
    final Matcher matcher = pattern.matcher(uri);
    if (matcher.matches()) {
      return getKey(matcher);
    } else {
      throw new BadRequestException("Request should match %s", pattern.pattern());
    }
  }

  private Json create(final Key<T> key, final HttpServletRequest request,
      final HttpServletResponse response)
      throws Throwable {
    final JsonMap data = parseData(request);
    return create(key, request, response, data);
  }

  protected Key<T> getKey(final Matcher m) {
    return Key.$(getType(m), m.group(2));
  }

  private JsonMap parseData(final HttpServletRequest request)
      throws ServletException, IOException {
    return switch (parse(request.getContentType())) {
      case MULTIPART_FORMDATA -> throw new ServletException(new UnsupportedOperationException());
      case URL_ENCODED -> parseUrlEncoded(request);
      default-> JsonMap.parse(request.getInputStream());
    };
  }

  public JsonMap create(final Key<T> key, final HttpServletRequest request,
      final HttpServletResponse response,
      final JsonMap data) {
    return createObject(key, request, response, data, arg -> (JsonMap) toJson(key, arg, request),
        arg -> postCreate(arg, request, response));
  }

  @SuppressWarnings("unchecked")
  private Class<T> getType(final Matcher matcher) {
    final String typeName = matcher.group(1);
    final Class<T> type = (Class<T>) types.get(typeName);
    if (type == null) {
      log.warn(()->"Could not find persistent object type %s".formatted(typeName));
    }
    return type;
  }


  private JsonMap parseUrlEncoded(final HttpServletRequest request)
      throws IOException {
    String body = Strings.readToString(request.getInputStream());
    if (body.endsWith("\n")) {
      body = body.substring(0, body.length() - 1);
    }
    final String[] pairs = body.split("&");
    final JsonMap map = new JsonMap();
    for (final String pair : pairs) {
      final String[] keyValue = pair.split("=");
      map.put(keyValue[0], keyValue[1]);
    }
    return map;
  }

  protected Json toJson(final Key<T> key, final T t, final HttpServletRequest request) {
    return t == null ? Json.NULL : Info.$(t).toJson(t);
  }

  protected void postCreate(final T t, final HttpServletRequest request,
      final HttpServletResponse response) {

  }

  @SuppressWarnings("unchecked")
  protected Json getAll(final HttpServletRequest request) {
    final Key<T> key = getKey(request);
    if (this instanceof Listable) {
      return Listable.$(key.type, (Listable<T>) this, request);
    } else {
      throw new BadRequestException("%s is not listable", getClass().getSimpleName());
    }
  }

  protected boolean isReadAuthorized(final HttpServletRequest request, final T t) {
    return isAuthorized(request, t);
  }

  protected Query<T> lookup(final Class<T> type, final HttpServletRequest request) {
    return Query.all(type);
  }

  public SortedQuery<T> orderBy(final Query<T> query, final String column,
      final OrderBy.Direction direction) {
    return query.orderBy(column, direction);
  }

  public Query<T> search(final String query) {
    throw new BadRequestException("Must implement search()");
  }

  /**
   * Can optionally be overridden differently from toJson() to provide additional values in list
   * calls
   */
  protected Json toListJson(final Key<T> key, final T object, final HttpServletRequest request) {
    return toJson(key, object, request);
  }
}
