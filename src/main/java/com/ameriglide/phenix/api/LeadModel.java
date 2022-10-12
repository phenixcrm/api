package com.ameriglide.phenix.api;

import com.ameriglide.phenix.Auth;
import com.ameriglide.phenix.common.*;
import com.ameriglide.phenix.model.Key;
import com.ameriglide.phenix.model.ListableModel;
import com.ameriglide.phenix.servlet.exception.ForbiddenException;
import com.ameriglide.phenix.ws.Events;
import com.ameriglide.phenix.ws.ReminderHandler;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.potion.Locator;
import net.inetalliance.potion.info.Info;
import net.inetalliance.potion.query.*;
import net.inetalliance.sql.*;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonList;
import net.inetalliance.types.json.JsonMap;

import java.io.IOException;
import java.time.*;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.ameriglide.phenix.common.Opportunity.*;
import static com.ameriglide.phenix.core.Strings.isEmpty;
import static com.ameriglide.phenix.core.Strings.isNotEmpty;
import static com.ameriglide.phenix.twilio.TaskRouter.toUS10;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.regex.Pattern.compile;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static net.inetalliance.sql.OrderBy.Direction.ASCENDING;
import static net.inetalliance.sql.OrderBy.Direction.DESCENDING;

@WebServlet("/api/lead/*")
public class LeadModel extends ListableModel<Opportunity> {

  private static final Pattern space = compile("[ @.]");
  private static final Pattern spaces = compile(" +");
  private static final Pattern or = compile("( \\| )|( OR )", CASE_INSENSITIVE);

  public LeadModel() {
    super(Opportunity.class, compile("/api/lead(?:/([^/]*))?"));
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
        .$("business",
          new JsonMap().$("name", o.getBusiness().getName()).$("abbreviation", o.getBusiness().getAbbreviation()));

      var notes = new JsonList();
      extra.$("notes", notes);
      var n = Locator.$1(Note.withOpportunity(o));
      if(n != null) {
        var a = n.getAuthor();
        notes.add(new JsonMap().$("id", n.id).$("note", n.getNote()).$("author", a==null ? "Unknown":a.getFullName()).$("created", n.getCreated()));
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
  public Query<Opportunity> all(final Class<Opportunity> type, final HttpServletRequest request) {
    final boolean support = request.getParameter("support")!=null;
    final boolean review = request.getParameter("review")!=null;
    final boolean asap = request.getParameter("asap")!=null;
    final boolean digis = request.getParameter("digis")!=null;
    final SortField sort = SortField.from(request);
    final Agent loggedIn = Auth.getAgent(request);
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
      query = query.and(Opportunity.withSources(Arrays
        .stream(sources)
        .map(Source::valueOf)
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(Source.class)))));
    }

    final String q = request.getParameter("q");
    boolean onlySold = false;

    final String heat = request.getParameter("h");
    if (isNotEmpty(heat)) {
      switch (heat) {
        case "CLOSED" -> query = query.and(isClosed);
        case "SOLD" -> {
          onlySold = true;
          query = query.and(isSold);
        }
        case "DEAD" -> query = query.and(isDead);
        case "OPEN" -> query = query.and(isActive);
      }
    } else if (isEmpty(q) && !(support || review)) {
      query = query.and(isActive);
    }

    Supplier<Set<Agent>> viewable = () -> Locator.$$(Agent.viewableBy(loggedIn));

    if (support || review || asap || digis) {
      final String[] as = request.getParameterValues("a");
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
    }

    final Range c = getParameter(request, Range.class, "c");
    if (c!=null) {
      query = query.and(Opportunity.createdInInterval(c.toInterval()));
    }
    if (

      isEmpty(q)) {
      return query;
    }
    return

      buildSearchQuery(query, q);
  }

  protected Query<Opportunity> sort(Query<Opportunity> base, SortField f) {
    return switch (f.field) {
      case "productLine" -> Query
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

  @Override
  protected void delete(final HttpServletRequest request, final HttpServletResponse response) {
    throw new UnsupportedOperationException();
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

  @Override
  protected void setDefaults(final Opportunity opportunity, final HttpServletRequest request, final JsonMap data) {
    data.put("created", LocalDateTime.now());
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
