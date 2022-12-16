package com.ameriglide.phenix.api.reporting;

import com.ameriglide.phenix.common.*;
import com.ameriglide.phenix.core.Log;
import com.ameriglide.phenix.servlet.exception.BadRequestException;
import com.ameriglide.phenix.servlet.exception.NotFoundException;
import com.ameriglide.phenix.servlet.exception.UnauthorizedException;
import com.ameriglide.phenix.util.ProgressMeter;
import jakarta.servlet.annotation.WebServlet;
import net.inetalliance.potion.Locator;
import net.inetalliance.potion.info.Info;
import net.inetalliance.potion.query.Query;
import net.inetalliance.sql.*;
import net.inetalliance.types.Currency;
import net.inetalliance.types.json.JsonList;
import net.inetalliance.types.json.JsonMap;

import javax.swing.text.Segment;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ameriglide.phenix.common.Call.*;
import static com.ameriglide.phenix.common.Source.WEB;
import static com.ameriglide.phenix.core.Strings.isEmpty;
import static java.util.stream.Collectors.toSet;
import static net.inetalliance.potion.Locator.*;
import static net.inetalliance.sql.Aggregate.SUM;

@WebServlet({"/reporting/reports/agentClosing"})
public class AgentClosing extends CachedGroupingRangeReport<Agent, Business> {

  private static final Log log = new Log();
  private final Info<Business> info;

  public AgentClosing() {
    super("business", "agent", "uniqueCid", "noTransfers");
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
  protected Business getGroup(final String[] params, final String key) {
    return info.lookup(key);
  }

  @Override
  protected int getJobSize(final Agent loggedIn, final Set<Business> groups, final DateTimeInterval intervalStart) {
    return count(allRows(groups, loggedIn, intervalStart.start()));
  }

  @Override
  protected Query<Agent> allRows(Set<Business> groups, final Agent loggedIn, final LocalDateTime start) {
    return Agent.viewableBy(loggedIn).and(Agent.isActive).and(Agent.sales);
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
    final String agentKey = getSingleExtra(extras, "agent", "");
    if (isEmpty(agentKey)) {
      throw new BadRequestException("Must specify agent via ?agent=");
    }
    final Agent agent = $(new Agent(Integer.parseInt(agentKey)));
    if (agent==null) {
      throw new NotFoundException("Could not find agent with key %s", agentKey);
    }

    final Query<Opportunity> oppSources;
    final Query<Call> callSources;

    if (sources.isEmpty()) {
      oppSources = Opportunity.online.negate();
      callSources = Call.withSourceIn(EnumSet.of(WEB)).negate();
    } else {
      oppSources = Opportunity.withSources(sources);
      callSources = Call.withSourceIn(sources);
    }

    boolean uniqueCid = Boolean.parseBoolean(getSingleExtra(extras, "uniqueCid", "false"));
    boolean noTransfers = Boolean.parseBoolean(getSingleExtra(extras, "noTransfers", "false"));

    final Set<Integer> queues = Locator.$A(SkillQueue.class).stream().map(q -> q.id).collect(toSet());
    ProductLineClosing.retainVisible(loggedIn, businesses, queues);

    final Query<Call> callQuery = Call.inInterval(interval);
    final Query<Opportunity> oppQuery = Opportunity
      .soldInInterval(interval)
      .and(Opportunity.withAgent(agent))
      .and(oppSources)
      .and(
        businesses==null || businesses.isEmpty() ? Query.all(Opportunity.class):Opportunity.withBusiness(businesses));
    final JsonList rows = new JsonList();
    Locator.forEach(Query.all(ProductLine.class), productLine -> {
      final AtomicInteger n = new AtomicInteger(0);
      var productLineQueues = new HashSet<>(queues);
      productLineQueues.retainAll(ProductLineClosing.getQueues(loggedIn,productLine,businesses));
      final Query<Call> productLineCallQuery = productLineQueues.isEmpty() ? Query.none(Call.class):callQuery.and(
        Call.withQueueIn(productLineQueues));
      var productLineTotal = new JsonMap();
      Query<Call> queueQuery = productLineCallQuery.and(isQueue).and(callSources).and(withAgent(agent));
      if (noTransfers) {
        queueQuery = noTransfers(queueQuery);

      }
      var queueCalls = uniqueCid ? countDistinct(queueQuery, "phone"):count(queueQuery);
      productLineTotal.put("queueCalls", queueCalls);

      final Query<Call> outboundQuery = productLineCallQuery.and(isOutbound).and(withAgent(agent));
      if (uniqueCid) {
        productLineTotal.put("outbound", countDistinct(outboundQuery.join(Leg.class, "call"), "leg.phone"));

      } else {
        productLineTotal.put("outbound",count(outboundQuery));
      }
      productLineTotal.put("dumps",count(
        productLineCallQuery.and(isQueue).and(withAgent(agent).negate()).and(Call.isDumped).and(withAgent(agent))));
      final Query<Opportunity> agentOppQuery = oppQuery.and(Opportunity.withProductLine(productLine));
      var closes = count(agentOppQuery);
      productLineTotal.put("closes",closes);
      productLineTotal.put("sales",$$(agentOppQuery, SUM, Currency.class, "amount"));

      if (closes > 0 || queueCalls > 0) {
        n.incrementAndGet();
        rows.add(productLineTotal.$("label", productLine.getName()).$("id", productLine.id));
      }
      meter.increment(productLine.getName());
    }); return new JsonMap().$("rows", rows);

  }

  static Query<Call> noTransfers(Query<Call> queueQuery) {
    return queueQuery.and(new Query<>(Call.class, c -> Locator.count(Leg.withCall(c))==1, (namer, s) -> {
      var sql = new SqlBuilder(namer.name(Segment.class), null, new AggregateField(Aggregate.COUNT, "*"));
      sql.where(new ColumnWhere("call", "sid", "leg", "call", false));
      return new SubqueryValueWhere(sql.getSql(), 1);
    }));
  }
}
