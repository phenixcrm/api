package com.ameriglide.phenix.api;

import com.ameriglide.phenix.Auth;
import com.ameriglide.phenix.Startup;
import com.ameriglide.phenix.common.*;
import com.ameriglide.phenix.core.Log;
import com.ameriglide.phenix.core.Optionals;
import com.ameriglide.phenix.model.Key;
import com.ameriglide.phenix.model.ListableModel;
import com.ameriglide.phenix.model.Range;
import com.ameriglide.phenix.servlet.PhenixServlet;
import com.ameriglide.phenix.servlet.exception.BadRequestException;
import com.ameriglide.phenix.servlet.exception.ForbiddenException;
import com.ameriglide.phenix.servlet.exception.NotFoundException;
import com.ameriglide.phenix.servlet.exception.UnauthorizedException;
import com.ameriglide.phenix.types.Resolution;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.potion.Locator;
import net.inetalliance.potion.info.Info;
import net.inetalliance.potion.query.Query;
import net.inetalliance.types.geopolitical.Country;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonList;
import net.inetalliance.types.json.JsonMap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.ameriglide.phenix.core.Optionals.of;
import static com.ameriglide.phenix.core.Strings.isEmpty;
import static com.ameriglide.phenix.twilio.TaskRouter.toUS10;
import static com.ameriglide.phenix.types.CallDirection.INTERNAL;
import static com.ameriglide.phenix.types.CallDirection.QUEUE;
import static com.ameriglide.phenix.types.Resolution.ACTIVE;
import static com.ameriglide.phenix.types.Resolution.ANSWERED;
import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.util.regex.Pattern.compile;
import static java.util.stream.Collectors.toSet;
import static net.inetalliance.potion.Locator.$$;
import static net.inetalliance.potion.Locator.forEach;
import static net.inetalliance.sql.OrderBy.Direction.ASCENDING;
import static net.inetalliance.sql.OrderBy.Direction.DESCENDING;

@WebServlet("/api/call/*")
public class CallModel extends ListableModel<Call> {

  private static final Pattern space = compile(" ");
  private static final Log log = new Log();
  public static final Set<String> validInfoUpdateFields = Set.of("lead", "contact");
  private List<JsonMap> simContacts;

  public CallModel() {
    super(Call.class);
  }

  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    simContacts = new ArrayList<>(100);
    var data = CallModel.class.getResourceAsStream("/callsim-contacts.csv");
    assert (data!=null);
    try (var reader = new BufferedReader(new InputStreamReader(data))) {
      String line;
      while ((line = reader.readLine())!=null) {
        var split = line.split(",");
        simContacts.add(new JsonMap().$("name", split[0]).$("phone", split[1]).$("postalCode", split[2]));
      }
      log.info(() -> "Loaded %d simulated contacts from %s".formatted(simContacts.size(), "/callsim-contacts.json"));

    } catch (IOException e) {
      throw new ServletException(e);
    }
  }

  @Override
  public Query<Call> all(final Class<Call> type, final HttpServletRequest request) {
    final boolean simulated = request.getParameter("simulated")!=null;
    final boolean todo = request.getParameter("todo")!=null;
    final Agent agent = Auth.getAgent(request);
    if (simulated && !Auth.isTeamLeader(request)) {
      throw new ForbiddenException("%s tried to access call simulator", agent.getLastNameFirstInitial());
    }
    if (simulated) {
      return Call.simulated.and(Call.withAgentIn($$(Agent.viewableBy(agent))));
    } else if (todo) {
      return Call.isTodo.and(Call.withAgent(agent)).orderBy("created", DESCENDING).limit(25);
    } else if (isEmpty(request.getParameter("n"))) {
      throw new BadRequestException("Sorry, but you can't ask for all the calls. There's like a bajillion of them.");
    } else {

      final String[] ss = request.getParameterValues("s");
      Query<Call> withSite = ss==null || ss.length==0 ? Query.all(Call.class):Call.withChannelIdIn(
        Arrays.stream(ss).map(Integer::valueOf).collect(toSet()));

      final Range c = getParameter(request, Range.class, "c");
      if (c!=null) {
        withSite = withSite.and(Call.inInterval(c.toDateTimeInterval()));
      }
      return Call.isQueue.and(Call.withChannel($$(Channel.withAgent(agent))))
        .and(withSite)
        .and(request.getParameter("silent")==null ? Call.isShort:Query.all(Call.class))
        .and(super.all(type, request))
        .orderBy("created", DESCENDING);
    }
  }

  @Override
  protected Json update(final Key<Call> key, final HttpServletRequest request, final HttpServletResponse response,
                        final Call call, final JsonMap data) throws IOException {
    if (call.sid.startsWith("SIM") && data.getEnum("resolution", Resolution.class)==ANSWERED) {

      final Leg leg = call.getActiveLeg();
      Locator.update(leg, "call-sim", copy -> {
        copy.setEnded(LocalDateTime.now());
      });
      Locator.update(call, "call-sim", arg -> {
        arg.setResolution(ANSWERED);
      });
      return JsonMap.singletonMap("success", true);
    } else if (data.containsKey("todo")) {
      // only allow flipping of the t-o-d-o flag.
      return super.update(key, request, response, call, new JsonMap().$("todo", data.getBoolean("todo")));
    } else if (data.containsKey("reviewed")) {
      // only allow flipping of the reviewed flag.
      return super.update(key, request, response, call, new JsonMap().$("reviewed", data.getBoolean("reviewed")));
    } else {
        if(validInfoUpdateFields.containsAll(data.keySet())) {
        var json = new JsonMap();
        json.putAll(data);
        return super.update(key, request, response, call, json);
      }
      else {
        throw new UnsupportedOperationException();
      }
    }
  }

  @Override
  public JsonMap create(final Key<Call> key, final HttpServletRequest request, final HttpServletResponse response,
                        final JsonMap data) {
    if (Optionals.of(data.getBoolean("simulated")).orElse(false)) {
      final Agent manager = Auth.getAgent(request);
      if (!Auth.isTeamLeader(request)) {
        throw new ForbiddenException("%s tried to create a simulated call", manager.getLastNameFirstInitial());
      }
      var agent = Locator.$(new Agent(data.getInteger("agent")));
      if (!manager.canSee(agent)) {
        throw new ForbiddenException("%s tried to create a simulated call for a different manager's agent (%d)",
          manager.getLastNameFirstInitial(), agent.id);
      }
      var channel = Locator.$(new Channel(data.getInteger("channel")));
      if (channel==null) {
        throw new NotFoundException("Could not find channel %d", data.getInteger("channel"));
      }
      var product = Locator.$(new ProductLine(data.getInteger("productLine")));
      if (product==null) {
        throw new NotFoundException("Could not find product line %d", data.getInteger("productLine"));
      }
      final Call call = new Call(format("SIM%d", currentTimeMillis()));
      call.setChannel(channel);
      call.setQueue(Startup.router.getQueue("sales"));

      if (call.getQueue()==null) {
        return new JsonMap()
          .$("success", false)
          .$("reason",
            format("could not find call queue for '%s' on %s", product.getName(), channel.getAbbreviation()));
      }
      Collections.shuffle(simContacts);
      var c = simContacts.getFirst();
      call.setDirection(QUEUE);
      call.setResolution(ACTIVE);
      call.setName(c.get("name"));
      call.setCountry(Country.UNITED_STATES);
      call.setPhone(c.get("phone"));
      call.setZip(c.get("postalCode"));
      call.setSource(Source.SIMULATED);
      var now = LocalDateTime.now();
      call.setCreated(now);
      call.setAgent(agent);
      Locator.create("call-sim", call);
      final Leg leg = new Leg(call, "0");
      leg.setAgent(agent);
      leg.setCreated(now);
      leg.setAnswered(now);
      Locator.create("call-sim", leg);
      return JsonMap.singletonMap("success", true);
    }
    throw new UnsupportedOperationException();
  }

  @Override
  protected Json toJson(final Key<Call> key, final Call call, final HttpServletRequest request) {
    final JsonMap map = new JsonMap().$("sid").$("direction");
    key.info.fill(call, map);
    map.$("todo", call.isTodo());
    switch (call.getDirection()) {
      case OUTBOUND, QUEUE, VIRTUAL -> {
        final Channel channel = of(call.getChannel()).orElseGet(Channel.getDefault);
        map.$("channel", new JsonMap().$("name", channel.getName()).$("id", channel.id));
        if (call.getQueue()!=null) {
          final JsonMap queue = new JsonMap();
          queue.$("name", call.getQueue().getName());
          map.$("queue", queue);
        }
      }
    }
    final CNAM remoteCallerId = call.getRemoteCaller();
    if (remoteCallerId!=null) {
      final String number = remoteCallerId.getPhone();
      map.$("created", call.getCreated());
      map.$("localTime", Optionals.of(AreaCodeTime.getAreaCodeTime(toUS10(number))).map(AreaCodeTime::getTimeZone).orElse(null));
      map.$("callerId", new JsonMap().$("name", remoteCallerId.getName()).$("number", number));
      final JsonList contactMatches;
      if (isEmpty(number)) {
        contactMatches = new JsonList();
      } else {
        contactMatches = $$(Contact.withPhoneNumber(number.charAt(0)=='1' ? number.substring(1):number))
          .stream()
          .map(c -> {
            final JsonMap contactMap = new JsonMap().$("firstName").$("lastName").$("id");
            Info.$(c).fill(c, contactMap);
            return contactMap;
          })
          .collect(Collectors.toCollection(JsonList::new));
      }
      final String[] split = isEmpty(remoteCallerId.getName()) ? new String[]{""}:space.split(remoteCallerId.getName(),
        2);
      contactMatches.add(new JsonMap()
        .$("firstName", split.length==1 ? null:split[0])
        .$("lastName", split.length==2 ? split[1]:split[0]));
      map.$("contacts", contactMatches);
    }
    map.$("segments", (JsonList) $$(Leg.withCall(call).and(Leg.isAnswered)).stream().map(segment -> {
      final JsonMap j = new JsonMap();
      final Agent agent = segment.getAgent();
      if (agent!=null) {
        j.$("agent", format("%s %c", agent.getFirstName(), agent.getLastName().charAt(0)));
      }
      if (segment.getTalkTime()!=null) {
        j.$("talkTime", segment.getTalkTime());
      }
      return j;
    }).collect(Collectors.toCollection(JsonList::new)));

    return map;
  }

  @Override
  protected Json getAll(final HttpServletRequest request) {
    final JsonMap map = (JsonMap) super.getAll(request);
    final JsonMap filters = PhenixServlet.getFilters(request);
    map.$("filters", filters);
    if (request.getParameter("silent")!=null) {
      filters.put("silent", new JsonMap().$("silent", "Include Silent Calls"));
    }
    return map;
  }

  @Override
  public Json toJson(final HttpServletRequest request, Call call) {
    final boolean todo = request.getParameter("todo")!=null;
    var ticket = Auth.getTicket(request);
    if (ticket==null) {
      throw new UnauthorizedException();
    }
    final Agent agent = ticket.agent();
    final JsonMap map = (JsonMap) super.toJson(request, call);
    final ProductLine productLine = call.findProductLine();
    final CNAM remoteCid = call.getRemoteCaller();
    map.put("remoteCallerId", new JsonMap().$("name", remoteCid.getName()).$("number", remoteCid.getPhone()));
    if (!todo && (call.getDirection()!=INTERNAL)) {
      final JsonList contacts = new JsonList();
      map.put("contacts", contacts);
      final String number = remoteCid.getPhone();
      if (!isEmpty(number)
        && !"anonymous".equalsIgnoreCase(number)
        && !"blocked".equalsIgnoreCase(number)
        && number.length() > 4) {
        final Query<Contact> contactQuery = Contact.withPhoneNumber(number);
        forEach(contactQuery.orderBy("id", ASCENDING), contact -> {
          final JsonList leads = new JsonList();
          contacts.add(new JsonMap()
            .$("firstName", contact.getFirstName())
            .$("lastName", contact.getLastName())
            .$("id", contact.id)
            .$("leads", leads));
          forEach(Lead
            .withChannel($$(Channel.withAgent(agent)))
            .and(Lead.withContact(contact))
            .orderBy("created", ASCENDING), o -> {
            final Agent assignedTo = o.getAssignedTo();
            leads.add(new JsonMap()
              .$("id", o.id)
              .$("quote", o.getQuote())
              .$("stage", o.getHeat())
              .$("amount", o.getAmount())
              .$("saleDate", o.getSaleDate())
              .$("productLine", of(o.getProductLine()).map(ProductLine::getName).orElse(null))
              .$("created", o.getCreated())
              .$("assignedTo", assignedTo==null ? "Nobody":assignedTo.getLastNameFirstInitial()));

          });
        });
      }
    }
    final Contact contact = call.getContact();
    if (contact!=null) {
      map.$("contact", new JsonMap().$("firstName", contact.getFirstName()).$("lastName", contact.getLastName()));
    }
    return map
      .$("agent", call.getAgent()==null ? "None":call.getAgent().getLastNameFirstInitial())
      .$("channel", call.getChannel()==null ? "None":call.getChannel().getAbbreviation())
      .$("productLine", productLine==null ? "None":productLine.getAbbreviation());

  }

}