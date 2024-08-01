package com.ameriglide.phenix.api;

import com.ameriglide.phenix.Auth;
import com.ameriglide.phenix.common.*;
import com.ameriglide.phenix.core.Log;
import com.ameriglide.phenix.core.Optionals;
import com.ameriglide.phenix.model.Listable;
import com.ameriglide.phenix.servlet.PhenixServlet;
import com.ameriglide.phenix.servlet.exception.BadRequestException;
import com.ameriglide.phenix.servlet.exception.ForbiddenException;
import com.ameriglide.phenix.servlet.exception.NotFoundException;
import net.inetalliance.util.ProgressMeter;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.potion.Locator;
import net.inetalliance.potion.cache.RedisJsonCache;
import net.inetalliance.potion.info.Info;
import net.inetalliance.sql.DateTimeInterval;
import net.inetalliance.types.Duration;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonList;
import net.inetalliance.types.json.JsonMap;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static com.ameriglide.phenix.Auth.isManager;
import static com.ameriglide.phenix.common.Call.*;
import static com.ameriglide.phenix.core.Strings.isEmpty;
import static java.util.concurrent.TimeUnit.SECONDS;
import static net.inetalliance.potion.Locator.count;
import static net.inetalliance.potion.Locator.forEach;

@WebServlet("/api/review")
public class ReviewModel extends PhenixServlet {

  private static final Log log = new Log();
  private RedisJsonCache cache;

  @Override
  protected void get(final HttpServletRequest request, final HttpServletResponse response) throws Exception {
    var loggedIn = Auth.getAgent(request);
    var isManager = isManager(loggedIn);
    var teamParam = request.getParameter("team");
    final Team team;
    if (isEmpty(teamParam)) {
      if (isManager) {
        var iterator = Team.viewableBy(loggedIn).iterator();
        team = iterator.hasNext() ? iterator.next():null;
      } else {
        team = null;
      }
    } else {
      team = Locator.$(new Team(Integer.parseInt(teamParam)));
      if (team==null) {
        throw new NotFoundException("Could not find team %s", teamParam);
      }
      if (!loggedIn.isSuperUser() && (!isManager || !loggedIn.equals(team.getManager()))) {
        throw new ForbiddenException("%s tried to access team %s", loggedIn.getLastNameFirstInitial(), team.getName());
      }
    }

    var dayParam = request.getParameter("date");

    try {
      var now = LocalDate.now();
      var day = isEmpty(dayParam) ? now:american.parse(dayParam, LocalDate::from);
      var cacheKey = "Review:%s,%s,%s".formatted(day.toString(), loggedIn.isSuperUser(), teamParam);
      var allowCache = !day.plusDays(1).isAfter(now);
      if (allowCache) {
        var cached = cache.getMap(cacheKey);
        if (cached!=null) {
          respond(response, cached);
          return;
        }
      }
      var q = isQueue.and(isActive.negate()).and(inInterval(new DateTimeInterval(day)));
      if (team==null) {
        if (!loggedIn.isSuperUser()) {
          q = q.and(withAgent(loggedIn));
        }
      } else {
        var members = new HashSet<Agent>();
        team.toBreadthFirstIterator().forEachRemaining(t-> Locator.forEach(TeamMember.withTeam(t),members::add));
        q = q.and(withAgentIn(members).or(withBlameIn(members)));
      }
      int callCount = count(q);
      var json = new ArrayList<Json>(callCount);
      log.info("There are %d calls to process", callCount);
      var meter = new ProgressMeter(callCount);
      forEach(q, call -> {
        meter.increment();
        var callJson = new JsonMap()
          .$("sid")
          .$("created")
          .$("resolution")
          .$("todo")
          .$("dumped")
          .$("silent");
        Info.$(Call.class).fill(call, callJson);
        var callerId = call.getRemoteCaller();
        var channel = call.getChannel();
        var agent = call.getAgent();
        callJson
          .$("callerId", new JsonMap().$("name", callerId.getName()).$("number", callerId.getPhone()))
          .$("site", channel==null ? null:new JsonMap().$("abbreviation", channel.getAbbreviation()))
          .$("agent", agent==null ? null:new JsonMap().$("name", agent.getFirstNameLastInitial()).$("id", agent.id))
          .$("queue", call.getQueue().getName())
          .$("productLine", Optionals.of(call.getProductLine()).map(p -> p.id).orElse(null))
          .$("duration", new Duration(1000 * call.getDuration()).getAbbreviation(true));
        var blame = call.getBlame();
        callJson.$("blame",
          blame==null ? null:new JsonMap().$("name", blame.getFirstNameLastInitial()).$("id", blame.id));
        var contactQuery = Contact.withPhoneNumber(callerId.getPhone()).limit(3);
        int contactsCount = count(contactQuery);
        var contacts = new JsonList(contactsCount);
        var opps = new HashMap<Agent, Collection<Lead>>();
        forEach(contactQuery, contact -> {
          contacts.add(new JsonMap()
            .$("id", contact.id)
            .$("name", contact.getFullName())
            .$("selected", contact.equals(call.getContact())));
          forEach(Lead.withContact(contact).and(Lead.createdBefore(call.getCreated().plusHours(1))),
            o -> opps.computeIfAbsent(o.getAssignedTo(), a -> new ArrayList<>()).add(o));
        });
        callJson.$("contacts", contacts);
        var legQuery = Leg.withCall(call);
        var legs = new JsonList(count(legQuery));
        callJson.$("legs", legs);
        forEach(legQuery, leg -> {
          var a = leg.getAgent();
          var legJson = new JsonMap()
            .$("created", leg.getCreated())
            .$("answered", leg.getAnswered())
            .$("ended", leg.getEnded())
            .$("talktime",
              leg.getTalkTime()==null ? null:new Duration(SECONDS.toMillis(leg.getTalkTime())).getAbbreviation(true))
            .$("agent", a==null ? null:new JsonMap().$("name", a.getFirstNameLastInitial()).$("id", a.id));
          if (a!=null) {
            var oppList = opps
              .getOrDefault(a, Set.of())
              .stream()
              .map(o -> new JsonMap()
                .$("id", o.id)
                .$("notes", Note.latest(o))
                .$("stage", o.getHeat())
                .$("productLine", Optionals.of(o.getProductLine()).map(ProductLine::getName).orElse(null))
                .$("existing", o.getCreated().isBefore(call.getCreated()))
                .$("reminder", o.getReminder()))
              .collect(Collectors.toCollection(JsonList::new));
            legJson.$("opportunities", oppList);
          }
          legs.add(legJson);
        });
        json.add(callJson);
      });
      var result = Listable.formatResult(json);
      if (allowCache) {
        cache.set(cacheKey, result);
      }
      respond(response, result);
    } catch (IllegalArgumentException e) {
      log.error(e);
      throw new BadRequestException("Unparseable day specified: %s", dayParam);
    }
  }

  @Override
  public void init() throws ServletException {
    super.init();
    cache = new RedisJsonCache(getClass().getSimpleName());
  }
}
