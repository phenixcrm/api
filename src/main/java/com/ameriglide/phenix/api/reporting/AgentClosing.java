package com.ameriglide.phenix.api.reporting;

import com.ameriglide.phenix.common.*;
import com.ameriglide.phenix.core.Log;
import com.ameriglide.phenix.servlet.exception.BadRequestException;
import com.ameriglide.phenix.servlet.exception.NotFoundException;
import com.ameriglide.phenix.servlet.exception.UnauthorizedException;
import net.inetalliance.util.ProgressMeter;
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
public class AgentClosing extends CachedGroupingRangeReport<Agent, Channel> {

  private static final Log log = new Log();
  private final Info<Channel> info;

  public AgentClosing() {
    super("channel", "agent", "uniqueCid", "noTransfers");
    info = Info.$(Channel.class);
  }

  @Override
  protected String getGroupLabel(final Channel group) {
    return group.getName();
  }

  @Override
  protected String getId(final Agent row) {
    return row.id.toString();
  }

  @Override
  protected Channel getGroup(final String[] params, final String key) {
    return info.lookup(key);
  }

  @Override
  protected int getJobSize(final Agent loggedIn, final Set<Channel> groups, final DateTimeInterval intervalStart) {
    return count(allRows(groups, loggedIn, intervalStart.start()));
  }

  @Override
  protected Query<Agent> allRows(Set<Channel> groups, final Agent loggedIn, final LocalDateTime start) {
    return Agent.viewableBy(loggedIn).and(Agent.isActive).and(Agent.sales);
  }

  @Override
  protected JsonMap generate(final Set<Source> sources, final Agent loggedIn, final ProgressMeter meter,
                             final DateTimeInterval interval, final Set<Channel> channels, Collection<Team> teams,
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

    final Query<Lead> oppSources;
    final Query<Call> callSources;

    if (sources.isEmpty()) {
      oppSources = Lead.online.negate();
      callSources = Call.withSourceIn(EnumSet.of(WEB)).negate();
    } else {
      oppSources = Lead.withSources(sources);
      callSources = Call.withSourceIn(sources);
    }

    boolean uniqueCid = Boolean.parseBoolean(getSingleExtra(extras, "uniqueCid", "false"));
    boolean noTransfers = Boolean.parseBoolean(getSingleExtra(extras, "noTransfers", "false"));

    final Set<String> vCids = Locator.$A(VerifiedCallerId.class).stream().map(q -> q.sid).collect(toSet());
    ProductLineClosing.retainVisible(loggedIn, channels, vCids);

    final Query<Call> callQuery = Call.inInterval(interval);
    final Query<Lead> oppQuery = Lead
      .soldInInterval(interval)
      .and(Lead.withAgent(agent))
      .and(oppSources)
      .and(
        channels==null || channels.isEmpty() ? Query.all(Lead.class):Lead.withChannel(channels));
    final JsonList rows = new JsonList();
    Locator.forEach(Query.all(ProductLine.class), productLine -> {
      final AtomicInteger n = new AtomicInteger(0);
      var productLineVCids = new HashSet<>(vCids);
      productLineVCids.retainAll(ProductLineClosing.getVerifiedCids(loggedIn,productLine, channels));
      final Query<Call> productLineCallQuery = productLineVCids.isEmpty() ? Query.none(Call.class):callQuery.and(
        Call.withVerifiedCidIn(productLineVCids));
      var productLineTotal = new JsonMap();
      Query<Call> queueQuery = productLineCallQuery.and(isQueue).and(callSources).and(withAgent(agent));
      if (noTransfers) {
        queueQuery = noTransfers(queueQuery);

      }
      var queueCalls = uniqueCid ? countDistinct(queueQuery, "phone"):count(queueQuery);
      productLineTotal.put("queueCalls", queueCalls);

      var outboundQuery = productLineCallQuery.and(isOutbound).and(withAgent(agent));
      if (uniqueCid) {
        productLineTotal.put("outbound", countDistinct(outboundQuery.join(Leg.class, "call"), "leg.phone"));

      } else {
        productLineTotal.put("outbound",count(outboundQuery));
      }
      productLineTotal.put("dumps",count(
        productLineCallQuery.and(isQueue).and(withAgent(agent).negate()).and(Call.isDumped).and(withAgent(agent))));
      final Query<Lead> agentOppQuery = oppQuery.and(Lead.withProductLine(productLine));
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
