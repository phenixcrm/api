package com.ameriglide.phenix.api.reporting;

import com.ameriglide.phenix.common.*;
import com.ameriglide.phenix.core.Iterables;
import com.ameriglide.phenix.core.Log;
import com.ameriglide.phenix.servlet.exception.BadRequestException;
import com.ameriglide.phenix.servlet.exception.NotFoundException;
import com.ameriglide.phenix.servlet.exception.UnauthorizedException;
import net.inetalliance.util.ProgressMeter;
import jakarta.servlet.annotation.WebServlet;
import net.inetalliance.potion.Locator;
import net.inetalliance.potion.info.Info;
import net.inetalliance.potion.query.Query;
import net.inetalliance.sql.DateTimeInterval;
import net.inetalliance.types.Currency;
import net.inetalliance.types.json.JsonList;
import net.inetalliance.types.json.JsonMap;

import javax.swing.text.Segment;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.stream.Stream;

import static com.ameriglide.phenix.core.Strings.isEmpty;
import static java.util.stream.Collectors.toSet;
import static net.inetalliance.potion.Locator.count;
import static net.inetalliance.potion.Locator.countDistinct;
import static net.inetalliance.sql.Aggregate.SUM;

@WebServlet({"/reporting/reports/productLineClosing"})
public class ProductLineClosing extends CachedGroupingRangeReport<Agent, Business> {

  private static final Log log = new Log();
  private final Info<Business> info;

  public ProductLineClosing() {
    super("business", "productLine", "uniqueCid", "noTransfers");
    info = Info.$(Business.class);
  }

  @Override
  protected String getGroupLabel(final Business group) {
    return group.getName();
  }

  @Override
  protected String getId(final Agent row) {
    return row.id.toString();
  }

  @Override
  protected Query<Agent> allRows(final Set<Business> groups, final Agent loggedIn, final LocalDateTime intervalStart) {
    return Agent.viewableBy(loggedIn).and(Agent.isActive).and(Agent.sales);
  }

  @Override
  protected Business getGroup(final String[] params, final String key) {
    return info.lookup(key);
  }

  @Override
  protected int getJobSize(final Agent loggedIn, final Set<Business> groups, final DateTimeInterval interval) {
    return count(allRows(groups, loggedIn, interval.start()));
  }

  @Override
  protected JsonMap generate(final Set<Source> sources, final Agent loggedIn, final ProgressMeter meter,
                             final DateTimeInterval interval, final Set<Business> businesses, Collection<Team> teams,
                             final Map<String, String[]> extras) {
    if (loggedIn==null || !(loggedIn.isSuperUser())) {
      log.warn(
        () -> "%s tried to access closing report data".formatted(loggedIn==null ? "Nobody?":loggedIn.getFullName()));
      throw new UnauthorizedException();
    }
    final String[] productLineIds = extras.get("productLine");
    if (productLineIds.length==0 || isEmpty(productLineIds[0])) {
      throw new BadRequestException("Must specify product line via ?productLine=");
    }
    final Set<ProductLine> productLines = Arrays
      .stream(productLineIds)
      .map(id -> Locator.$(new ProductLine(Integer.valueOf(id))))
      .collect(toSet());
    if (productLines.isEmpty()) {
      throw new NotFoundException("Could not find product lines with ids %s", Arrays.toString(productLineIds));
    }
    boolean uniqueCid = Boolean.parseBoolean(getSingleExtra(extras, "uniqueCid", "false"));
    boolean noTransfers = Boolean.parseBoolean(getSingleExtra(extras, "noTransfers", "false"));

    final Set<String> vCids = productLines
      .stream()
      .map(pl -> getVerifiedCids(loggedIn, pl, businesses))
      .flatMap(Iterables::stream)
      .collect(toSet());

    final Query<Call> callQuery = Call.inInterval(interval).and(Call.withVerifiedCidIn(vCids));
    Query<Lead> oppQuery = Lead
      .soldInInterval(interval)
      .and(sources.isEmpty() ? Lead.online.negate():Lead.withSources(sources))
      .and(
        businesses==null || businesses.isEmpty() ? Query.all(Lead.class):Lead.withBusiness(businesses));
    if (!teams.isEmpty()) {
      var agents = teams
        .stream()
        .map(t -> Locator.$$(TeamMember.withTeam(t)))
        .flatMap(Iterables::stream)
        .map(a -> a.id)
        .collect(toSet());
      oppQuery = oppQuery.and(Lead.withAgentIdIn(agents));
    }
    final JsonList rows = new JsonList();
    final AtomicInteger totalCalls = new AtomicInteger(0);
    final AtomicInteger totalAgents = new AtomicInteger(0);
    final Map<Integer, AtomicInteger> teamCount = new HashMap<>();
    final Map<Integer, JsonMap> teamTotals = new HashMap<>();
    final Query<Lead> finalOppQuery = oppQuery;
    Locator.forEach(allRows(businesses, loggedIn, interval.start()), agent -> {
      meter.increment(agent.getLastNameFirstInitial());
      final Query<Call> agentCallQuery = callQuery.and(Call.withAgent(agent));
      final JsonMap agentTotal = new JsonMap();
      Query<Call> queueCallCountQuery = callQuery.and(Call.withAgent(agent)).and(Call.isQueue);
      if (!sources.isEmpty()) {
        queueCallCountQuery = queueCallCountQuery.and(Call.withSourceIn(sources));
      }
      if (noTransfers) {
        queueCallCountQuery = AgentClosing.noTransfers(queueCallCountQuery);
      }
      int queueCalls = uniqueCid ? countDistinct(queueCallCountQuery, "callerId_number"):count(queueCallCountQuery);
      agentTotal.put("queueCalls", queueCalls);
      final Query<Call> outboundQuery = callQuery.and(Call.withAgent(agent).and(Call.isOutbound));
      if (uniqueCid) {
        agentTotal.put("outboundCalls",
          countDistinct(outboundQuery.join(Segment.class, "call"), "segment.callerid_number"));

      } else {
        agentTotal.put("outboundCalls", count(outboundQuery));
      }
      agentTotal.put("dumps", count(agentCallQuery.and(Call.isQueue).and(Call.isDumped)));
      int closes = 0;
      Currency sales = Currency.ZERO;
      for (ProductLine productLine : productLines) {

        final Query<Lead> agentOppQuery = finalOppQuery
          .and(Lead.withProductLine(productLine))
          .and(Lead.withAgent(agent));
        closes += count(agentOppQuery);
        sales = sales.add(Locator.$$(agentOppQuery, SUM, Currency.class, "amount"));
      }
      agentTotal.put("closes", closes);
      agentTotal.put("sales", sales);
      if (closes > 0 || queueCalls > 0) {
        Locator.forEach(TeamMember.withAgent(agent), team -> {
          addInteger("closes", agentTotal, teamTotals.computeIfAbsent(team.id, k -> new JsonMap()));
          addInteger("outboundCalls", agentTotal, teamTotals.computeIfAbsent(team.id, k -> new JsonMap()));
          addInteger("queueCalls", agentTotal, teamTotals.computeIfAbsent(team.id, k -> new JsonMap()));
          addInteger("dumps", agentTotal, teamTotals.computeIfAbsent(team.id, k -> new JsonMap()));
          addCurrency("sales", agentTotal, teamTotals.computeIfAbsent(team.id, k -> new JsonMap()));
          teamCount.computeIfAbsent(team.id, k -> new AtomicInteger(0)).incrementAndGet();
        });
        totalAgents.incrementAndGet();
        totalCalls.addAndGet(queueCalls);
        rows.add(agentTotal.$("label", agent.getLastNameFirstInitial()).$("id", agent.id));
      }

    });
    for (final Team team : Team.viewableBy(loggedIn)) {
      final JsonMap teamTotal = teamTotals.computeIfAbsent(team.id, k -> new JsonMap());
      if (greaterThanZero(teamTotal.getInteger("closes"))|| greaterThanZero(teamTotal.getInteger("queueCalls"))) {
        rows.add(
          teamTotal.$("team", teamCount.getOrDefault(team.id, new AtomicInteger(0)).get()).$("label", team.getName()));
      }
    }
    return new JsonMap()
      .$("rows", rows)
      .$("total", new JsonMap().$("agents", totalAgents.get()).$("queueCalls", totalCalls.get()));

  }

  static Set<String> getVerifiedCids(final Agent loggedIn, final ProductLine productLine,
                                final Collection<Business> businesses) {
    final Set<String> allForProductLine = getVerifiedCids(productLine);
    final Set<String> queues = new HashSet<>(allForProductLine);
    retainVisible(loggedIn, businesses, queues);
    return queues;

  }

  private static boolean greaterThanZero(Integer amount) {
    return amount != null && amount > 0;
  }

  private static void addInteger(String key, JsonMap agentTotal, JsonMap teamTotal) {
    add(key, agentTotal, teamTotal, 0, JsonMap::getInteger, Integer::sum, JsonMap::put);
  }

  private static void addCurrency(String key, JsonMap agentTotal, JsonMap teamTotal) {
    add(key, agentTotal, teamTotal, Currency.ZERO, (map, k) -> {
      try {
        return Currency.parse(map.get(k));
      } catch (ParseException e) {
        throw new RuntimeException(e);
      }
    }, Currency::add, JsonMap::put);
  }

  static Set<String> getVerifiedCids(ProductLine productLine) {
    return Locator.$$(VerifiedCallerId.withProductLine(productLine)).stream().map(v -> v.sid).collect(toSet());
  }

  static void retainVisible(Agent loggedIn, Collection<Business> sites, Set<String> queues) {
    // todo: implement this logic

  }

  private static <N> void add(String key, JsonMap agentTotal, JsonMap teamTotal, N zero,
                              BiFunction<JsonMap, String, N> convert, BinaryOperator<N> sum,
                              TriConsumer<JsonMap, String, N> update) {
    update.apply(teamTotal, key,
      Stream.of(agentTotal, teamTotal).map(map -> convert.apply(map, key)).filter(Objects::nonNull).reduce(zero, sum));

  }

  @FunctionalInterface
  interface TriConsumer<A, B, C> {
    void apply(A a, B b, C c);
  }
}
