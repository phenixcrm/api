package com.ameriglide.phenix.ws;

import com.ameriglide.phenix.common.*;
import com.ameriglide.phenix.core.Log;
import com.ameriglide.phenix.core.Optionals;
import jakarta.websocket.Session;
import net.inetalliance.potion.Locator;
import net.inetalliance.potion.query.Query;
import net.inetalliance.types.Surnamed;
import net.inetalliance.types.json.JsonList;
import net.inetalliance.types.json.JsonMap;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static com.ameriglide.phenix.common.Lead.needsReminding;
import static com.ameriglide.phenix.common.Lead.withAgent;
import static com.ameriglide.phenix.core.Strings.isNotEmpty;
import static java.util.concurrent.TimeUnit.MINUTES;

public class ReminderHandler implements JsonMessageHandler, Runnable {

  private static final Log log = new Log();
  public static ReminderHandler $;
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor((r) -> {
    var t = new Thread(r);
    t.setDaemon(true);
    return t;
  });

  ReminderHandler() {
    $ = this;
    scheduler.scheduleWithFixedDelay(this, 0, 1, MINUTES);
  }

  @Override
  public void run() {
    final Map<TimeZone, Set<Ticket>> active = Events.getActiveAgents();
    try {
      active.forEach((timeZone, tickets) -> {
        var needsReminding = Lead.needsReminding(15, MINUTES);
        tickets.forEach(ticket -> notify(ticket.agent(), needsReminding));
      });

    } catch (Throwable t) {
      log.error(t);
    }
  }

  private static void notify(Agent agent, Query<Lead> needsReminding) {
    Events.broadcast("reminder", agent.id,
      JsonList.collect(Locator.$$(needsReminding.and(withAgent(agent))), ReminderHandler::toJson));

  }

  private static JsonMap toJson(Lead o) {
    final Contact c = o.getContact();
    final JsonList dial = new JsonList();
    final Address shipping = c.getShipping();
    if (shipping!=null && isNotEmpty(shipping.getPhone())) {
      dial.add(label("Shipping", shipping.getPhone()));
    }
    final Address billing = c.getBilling();
    if (billing!=null && isNotEmpty(billing.getPhone()) && (shipping==null || !Objects.equals(billing.getPhone(),
      (shipping.getPhone())))) {
      dial.add(label("Billing", billing.getPhone()));
    }

    return new JsonMap()
      .$("id", o.id)
      .$("reminder", o.getReminder())
      .$("heat", o.getHeat())
      .$("dial", dial)
      .$("contact", Optionals.of(o.getContact()).map(Surnamed::getFullName).orElse(""))
      .$("channel", o.getChannel().getAbbreviation())
      .$("productLine",
        JsonMap.$().$("name", o.getProductLine().getName()).$("abbreviation", o.getProductLine().getAbbreviation()))
      .$("amount", o.getAmount());
  }

  private static JsonMap label(final String label, final String phone) {
    return new JsonMap().$("label", label).$("phone", phone);
  }


  @Override
  public void onAsyncMessage(final List<Session> sessions, final JsonMap msg) {
    sessions.forEach(session -> onMessage(session, msg));
  }


  @Override
  public JsonMap onMessage(final Session session, final JsonMap msg) {
    broadcast(Events.getTicket(session));
    return null;
  }

  @Override
  public JsonMap onConnect(final Session session) {
    return onConnect(Events.getTicket(session));
  }

  public JsonMap onConnect(final Ticket agent) {
    broadcast(agent);
    return null;
  }

  @Override
  public void destroy() {
    scheduler.shutdownNow();
  }

  private void broadcast(Ticket ticket) {
    if (ticket!=null) {
      notify(ticket.agent(), needsReminding(15, MINUTES));
    }
  }


}
