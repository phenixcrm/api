package com.ameriglide.phenix.api;

import com.ameriglide.phenix.Auth;
import com.ameriglide.phenix.common.*;
import com.ameriglide.phenix.core.Consumers;
import com.ameriglide.phenix.core.Strings;
import com.ameriglide.phenix.model.Key;
import com.ameriglide.phenix.model.Listable;
import com.ameriglide.phenix.model.ListableModel;
import com.ameriglide.phenix.servlet.exception.ForbiddenException;
import com.ameriglide.phenix.ws.Events;
import com.ameriglide.phenix.ws.ReminderHandler;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.potion.Locator;
import net.inetalliance.potion.info.Info;
import net.inetalliance.potion.info.PersistenceError;
import net.inetalliance.potion.query.*;
import net.inetalliance.sql.*;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonList;
import net.inetalliance.types.json.JsonMap;
import org.postgresql.util.PSQLException;

import java.io.IOException;
import java.time.*;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static com.ameriglide.phenix.Auth.getAgent;
import static com.ameriglide.phenix.common.Heat.SOLD;
import static com.ameriglide.phenix.common.Opportunity.isActive;
import static com.ameriglide.phenix.common.Opportunity.isClosed;
import static com.ameriglide.phenix.core.Functions.throwing;
import static com.ameriglide.phenix.core.Strings.isEmpty;
import static com.ameriglide.phenix.core.Strings.isNotEmpty;
import static com.ameriglide.phenix.twilio.TaskRouter.toUS10;
import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.regex.Pattern.compile;
import static java.util.stream.Collectors.*;
import static net.inetalliance.sql.OrderBy.Direction.ASCENDING;
import static net.inetalliance.sql.OrderBy.Direction.DESCENDING;

@WebServlet("/api/lead/*")
public class LeadModel extends ListableModel<Opportunity> {

  private static final Pattern space = compile("[ @.]");
  private static final Pattern spaces = compile(" +");
  private static final Pattern or = compile("( \\| )|( OR )", CASE_INSENSITIVE);
  private static final Pattern supportedProducts = Pattern.compile(
    "/api/lead/([0-9]*)(?:/supportedProducts(?:/([0-9]*)(?:/serviceNotes)?))");

  public LeadModel() {
    super(Opportunity.class, compile("/api/lead(?:/([^/]*))?.*"));
  }

  @Override
  public Json toJson(final HttpServletRequest request, final Opportunity o) {
    return json(o);
  }

  public static JsonMap json(Opportunity o) {
    final JsonMap json = Info.$(o).toJson(o);
    if (o.id!=null) {
      final Contact c = o.getContact();
      var contact = new JsonMap();
      var extra = new JsonMap()
        .$("contact", contact.$("name", c.getFullName()))
        .$("state", c.getState()==null ? null:c.getState().getAbbreviation())
        .$("dealer", false)
        .$("amountNoCents", o.getAmount().toStringNoCents())
        .$("productLine",
          new JsonMap().$("name", o.getProductLine().getName()).$("abbreviation", o.getProductLine().getAbbreviation()))
        .$("assignedTo",
          new JsonMap().$("name", o.getAssignedTo().getLastNameFirstInitial()).$("id", o.getAssignedTo().id))
        .$("business", new JsonMap()
          .$("name", o.getBusiness().getName())
          .$("abbreviation", o.getBusiness().getAbbreviation())
          .$("uri", o.getBusiness().getUri()));

      var notes = new JsonList();
      extra.$("notes", notes);
      var n = Locator.$1(Note.withOpportunity(o));
      if (n!=null) {
        var a = n.getAuthor();
        notes.add(new JsonMap()
          .$("id", n.id)
          .$("note", n.getNote())
          .$("author", a==null ? "Unknown":a.getFullName())
          .$("created", n.getCreated()));
      }
      json.put("extra", extra);
    }
    final Contact contact = o.getContact();
    final String phone = contact.getPhone();
    if (isNotEmpty(phone)) {
      final AreaCodeTime time = AreaCodeTime.getAreaCodeTime(toUS10(phone));
      if (time==null) {
        json.put("localTime", (Integer) null);
      } else {
        json.put("localTime", ZonedDateTime.ofInstant(Instant.now(), time.getZoneId()).getOffset().getTotalSeconds());
      }
    }
    return json;

  }

  @Override
  protected void setDefaults(final Opportunity opportunity, final HttpServletRequest request, final JsonMap data) {
    data.put("created", LocalDateTime.now());
  }

  @Override
  public Query<Opportunity> all(final Class<Opportunity> type, final HttpServletRequest request) {
    final boolean support = request.getParameter("support")!=null;
    final boolean review = request.getParameter("review")!=null;
    final boolean asap = request.getParameter("asap")!=null;
    final boolean digis = request.getParameter("digis")!=null;
    final SortField sort = SortField.from(request);
    final Agent loggedIn = getAgent(request);
    final boolean teamLeader = Auth.isTeamLeader(request);
    if (review && !teamLeader) {
      throw new ForbiddenException("%s tried to access review section", loggedIn.getFullName());
    }
    Query<Opportunity> query;
    if (support) {
      query = isClosed;
    } else if (review) {
      query = Query.all(Opportunity.class);
    } else if (digis) {
      query = Opportunity.withSources(Set.of(Source.FORM, Source.SOCIAL, Source.REFERRAL));
    } else {
      query = Opportunity.withAgent(loggedIn);
    }
    query = sort(query, sort);

    final String[] pls = request.getParameterValues("pl");
    if (pls!=null && pls.length > 0) {
      query = query.and(Opportunity.withProductLineIdIn(Arrays.stream(pls).map(Integer::valueOf).collect(toList())));
    }
    final String[] bs = request.getParameterValues("b");
    if (bs!=null && bs.length > 0) {
      query = query.and(Opportunity.withBusinessIdIn(Arrays.stream(bs).map(Integer::valueOf).collect(toList())));
    }

    final String[] sources = request.getParameterValues("src");
    if (sources!=null && sources.length > 0) {
      query = query.and(Opportunity.withSources(
        Arrays.stream(sources).map(Source::valueOf).collect(toCollection(() -> EnumSet.noneOf(Source.class)))));
    }

    final String q = request.getParameter("q");
    boolean onlySold = false;

    final String[] heats = request.getParameterValues("h");
    if (heats!=null && heats.length > 0) {
      var selectedHeats = Arrays
        .stream(heats)
        .map(String::toUpperCase)
        .map(Heat::valueOf)
        .collect(toCollection(() -> EnumSet.noneOf(Heat.class)));
      query = query.and(Opportunity.withHeats(selectedHeats));
      onlySold = selectedHeats.size()==1 && selectedHeats.iterator().next()==SOLD;

    } else if (isEmpty(q) && !(support || review)) {
      query = query.and(isActive);
    }

    Supplier<Set<Agent>> viewable = () -> Locator.$$(Agent.viewableBy(loggedIn));

    if (support || review || asap || digis) {
      var as = request.getParameterValues("a");
      if (as!=null && as.length > 0) {
        if ((review || digis) && !loggedIn.isSuperUser()) {
          var viewableKeys = viewable.get().stream().map(a -> a.id.toString()).collect(toSet());
          Arrays.stream(as).filter(s -> !viewableKeys.contains(s)).findFirst().ifPresent(a -> {
            throw new ForbiddenException("%s tried to look at non-subordinates: %s in %s", loggedIn.getFullName(), a,
              as);
          });
        }
        query = query.and(Opportunity.withAgentIdIn(Arrays.stream(as).map(Integer::parseInt).collect(toSet())));
      } else if (review) {
        query = query.and(Agent.viewableBy(loggedIn).join(Opportunity.class, "assignedTo"));
      } else if (asap) {
        query = query.and(Opportunity.uncontacted).orderBy("created", ASCENDING);
      }
    }

    final Range ec = getParameter(request, Range.class, "ec");
    if (ec!=null) {
      if (onlySold) {
        query = query.and(Opportunity.soldInInterval(ec.toInterval()));
      } else {
        query = query.and(Opportunity.estimatedCloseInInterval(ec.toInterval()));
      }
    }
    final Range sd = getParameter(request, Range.class, "sd");
    if (sd!=null) {
      query = query.and(Opportunity.soldInInterval(sd.toInterval()));
    } else {
      var soldIn = getInterval(request, "sold");
      if (soldIn!=null) {
        query = query.and(Opportunity.soldInInterval(soldIn));
      }
    }

    final Range c = getParameter(request, Range.class, "c");
    if (c!=null) {
      query = query.and(Opportunity.createdInInterval(c.toInterval()));
    } else {
      var createdIn = getInterval(request, "created");
      if (createdIn!=null) {
        query = query.and(Opportunity.createdInInterval(createdIn));
      }
    }
    var remindIn = getInterval(request, "reminder");
    if (remindIn!=null) {
      query = query.and(Opportunity.withReminderIn(remindIn));
    }

    if (isEmpty(q)) {
      return query;
    }
    return

      buildSearchQuery(query, q);
  }

  @Override
  protected void get(final HttpServletRequest request, final HttpServletResponse response) throws Exception {
    var m = supportedProducts.matcher(request.getRequestURI());
    if (m.matches()) {
      try {
        var leadId = Integer.parseInt(m.group(1));
        var lead = Locator.$(new Opportunity(leadId));
        if (lead==null) {
          response.sendError(SC_NOT_FOUND);
        } else {
          var list = new JsonList();
          Locator.forEach(SupportedProduct.withOpportunity(lead), p -> {
            list.add(toJson(p));
          });
          respond(response, Listable.formatResult(list.size(), list));
        }
      } catch (NumberFormatException e) {
        response.sendError(SC_BAD_REQUEST, "could not parse id as number, given: %s".formatted(m.group(1)));
      }
    } else {
      super.get(request, response);
    }
  }

  @Override
  protected void post(final HttpServletRequest request, final HttpServletResponse response) throws Exception {
    var m = supportedProducts.matcher(request.getRequestURI());
    if (m.matches()) {
      try {
        var leadId = Integer.parseInt(m.group(1));
        var lead = Locator.$(new Opportunity(leadId));
        if (lead==null) {
          response.sendError(SC_NOT_FOUND);
        } else {
          var rawProductId = m.group(2);
          if (Strings.isEmpty(rawProductId)) { // creating a product
            var p = new SupportedProduct();
            Info.$(SupportedProduct.class).fromJson(p, parseData(request));
            p.setAdded(LocalDateTime.now());
            p.setOpportunity(lead);
            Locator.create(getAgent(request).getFullName(), p);
            respond(response, Info.$(p).toJson(p));
          } else { // creating a note for a product
            var productId = Integer.parseInt(rawProductId);
            var product = Locator.$(new SupportedProduct(productId));
            if (product==null) {
              response.sendError(SC_NOT_FOUND);
            } else {
              var n = new ServiceNote();
              n.setAuthor(Auth.getAgent(request));
              n.setCreated(LocalDateTime.now());
              n.setSupportedProduct(product);
              n.setNote(parseData(request).get("note"));
              if (Strings.isEmpty(n.getNote())) {
                response.sendError(SC_BAD_REQUEST, "must specify note to add");
              } else {
                Locator.create(n.getAuthor().getFullName(), n);
                respond(response, Info.$(ServiceNote.class).toJson(n).$("author",n.getAuthor().getFullName()));
              }
            }

          }

        }

      } catch (NumberFormatException e) {
        response.sendError(SC_BAD_REQUEST, e.getMessage());
      }
    } else {
      super.post(request, response);
    }
  }

  @Override
  protected void delete(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
    forProduct(request, response, Consumers.throwing((product, data) -> {
      try {
        Locator.delete(Auth.getAgent(request).getFullName(), product);
        respond(response, "{}");
      } catch (PersistenceError e) {
        var cause = e.getCause();
        if (cause instanceof PSQLException p) {
          var constraint = p.getServerErrorMessage().getConstraint();
          if ("fk_supportedproduct".equals(constraint)) {
            respond(response,
              JsonMap.singletonMap("error", "You can not remove a tracked product that has service notes"));
            return;
          }
        }
        throw e;
      }

    }), throwing(() -> {
      throw new UnsupportedOperationException();
    }));
  }

  @Override
  protected void put(final HttpServletRequest request, final HttpServletResponse response) throws Exception {
    forProduct(request, response, (product, data) -> {
      data.remove("opportunity");
      data.remove("id");
      data.remove("notes");
      Locator.update(product, Auth.getAgent(request).getFullName(), copy -> {
        Info.$(SupportedProduct.class).fromJson(copy, data);
      });
    }, throwing(() -> super.put(request, response)));

  }

  @Override
  protected Json update(final Key<Opportunity> key, final HttpServletRequest request,
                        final HttpServletResponse response, final Opportunity opportunity, final JsonMap data) throws
    IOException {
    try {
      return super.update(key, request, response, opportunity, data);
    } finally {
      if (data.containsKey("reminder") && ReminderHandler.$!=null) {
        ReminderHandler.$.onConnect(Events.getTicket(opportunity.getAssignedTo()));
      }
    }
  }

  @Override
  protected Json toJson(final Key<Opportunity> key, final Opportunity opportunity, final HttpServletRequest request) {
    return LeadModel.json(opportunity);
  }

  @Override
  protected Json getAll(final HttpServletRequest request) {
    final JsonMap map = (JsonMap) super.getAll(request);
    map.$("filters", getFilters(request));
    return map;

  }

  private void forProduct(final HttpServletRequest request, final HttpServletResponse response,
                          BiConsumer<SupportedProduct, JsonMap> ifPresent, Runnable orElse) throws IOException {
    var m = supportedProducts.matcher(request.getRequestURI());
    if (m.matches()) {
      try {
        var id = Integer.parseInt(m.group(2));
        var product = Locator.$(new SupportedProduct(id));
        if (product==null) {
          response.sendError(SC_NOT_FOUND);
        } else {
          ifPresent.accept(product, parseData(request));
        }
      } catch (NumberFormatException | ServletException e) {
        response.sendError(SC_BAD_REQUEST, "supported product keys must be numbers");
      }
    } else {
      orElse.run();
    }
  }

  private Json toJson(SupportedProduct p) {
    var json = Info.$(p).toJson(p);
    var notes = new JsonList();
    Locator.forEach(ServiceNote.withSupportedProduct(p), n -> {
      notes.add(Info.$(n).toJson(n).$("author", n.getAuthor().getFullName()));
    });
    json.$("notes", notes);
    return json;
  }

  protected Query<Opportunity> sort(Query<Opportunity> base, SortField f) {
    return switch (f.field) {
      case "productLine", "product" -> Query
        .all(ProductLine.class)
        .join(Opportunity.class, "productLine")
        .and(base)
        .orderBy("productLine.name", f.direction, false);
      case "customer" -> Query
        .all(Contact.class)
        .join(Opportunity.class, "contact")
        .and(base)
        .orderBy("contact.lastName", f.direction, false)
        .orderBy("contact.firstName", ASCENDING, false);
      case "state" -> Query
        .all(Contact.class)
        .join(Opportunity.class, "contact")
        .and(base)
        .orderBy("shipping_state", f.direction, false);
      case "business" -> Query
        .all(Business.class)
        .join(Opportunity.class, "business")
        .and(base)
        .orderBy("business.name", f.direction, false);
      case "assignedTo" -> Query
        .all(Agent.class)
        .join(Opportunity.class, "assignedTo")
        .and(base)
        .orderBy("agent.lastname", f.direction, false)
        .orderBy("agent.firstName", ASCENDING, false);
      default -> base.orderBy(f.field, f.direction);
    };
  }

  public static Query<Opportunity> buildSearchQuery(final Query<Opportunity> query, String searchQuery) {
    searchQuery = searchQuery.replaceAll("[-()]", "");
    searchQuery = spaces.matcher(searchQuery).replaceAll(" ");
    searchQuery = or.matcher(searchQuery).replaceAll("|");
    final String[] terms = space.split(searchQuery);
    final SortedQuery<Opportunity> delegate = query
      .and(new Query<>(Opportunity.class, (p) -> {
        throw new UnsupportedOperationException();
      }, (namer, table) -> new ColumnWhere(table, "contact", namer.name(Contact.class), "id")))
      .orderBy("combined_rank", DESCENDING, false)
      .and(new Search<>(Opportunity.class, terms).or(
        new Join<>(new Search<>(Contact.class, terms), Opportunity.class, Opportunity::getContact)));
    return new DelegatingQuery<>(delegate, s -> 'S' + s, Function.identity(), b -> b) {
      @Override
      public Iterable<Object> build(final SqlBuilder sql, final Namer namer, final DbVendor vendor,
                                    final String table) {
        if (sql.aggregateFields.length==0) {
          sql.addColumn(null, "ts_rank(Opportunity.document,Opportunity_query) + ts_rank(Contact.document,"
            + "Contact_query) AS combined_rank");
        }
        return super.build(sql, namer, vendor, table);
      }

    };
  }

  @SuppressWarnings("unused")
  public enum Range {
    DAY() {
      @Override
      public DateTimeInterval toInterval() {
        var today = LocalDate.now().atStartOfDay();
        return new DateTimeInterval(today, today.plusDays(1));
      }
    }, WEEK() {
      @Override
      public DateTimeInterval toInterval() {
        var start = LocalDate.now().with(DayOfWeek.SUNDAY).atStartOfDay();
        return new DateTimeInterval(start, start.plusWeeks(1));
      }
    }, MONTH() {
      @Override
      public DateTimeInterval toInterval() {
        var start = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        return new DateTimeInterval(start, start.plusMonths(1));
      }
    }, YEAR() {
      @Override
      public DateTimeInterval toInterval() {
        var start = LocalDate.now().withDayOfYear(1).atStartOfDay();
        return new DateTimeInterval(start, start.plusYears(1));
      }
    }, N30() {
      public DateTimeInterval toInterval() {
        var start = LocalDate.now().atStartOfDay();
        return new DateTimeInterval(start, start.plusDays(30));
      }
    }, N90() {
      public DateTimeInterval toInterval() {
        var start = LocalDate.now().atStartOfDay();
        return new DateTimeInterval(start, start.plusDays(90));
      }
    }, L30() {
      @Override
      public DateTimeInterval toInterval() {
        var start = LocalDate.now().atStartOfDay();
        return new DateTimeInterval(start.minusDays(30), start);
      }
    }, L90() {
      @Override
      public DateTimeInterval toInterval() {
        var start = LocalDate.now().atStartOfDay();
        return new DateTimeInterval(start.minusDays(90), start);
      }
    };

    abstract public DateTimeInterval toInterval();
  }

  public record SortField(String field, OrderBy.Direction direction) {

    static SortField from(final HttpServletRequest request) {
      final String raw = request.getParameter("sort");
      if (raw==null) {
        return new SortField("created", DESCENDING);
      }
      var desc = raw.startsWith("-");
      return new SortField(desc ? raw.substring(1):raw, desc ? DESCENDING:ASCENDING);
    }
  }
}
