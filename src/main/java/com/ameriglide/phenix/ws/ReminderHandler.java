package com.ameriglide.phenix.ws;

import com.ameriglide.phenix.common.Address;
import com.ameriglide.phenix.common.Contact;
import com.ameriglide.phenix.common.Opportunity;
import com.ameriglide.phenix.common.Ticket;
import com.ameriglide.phenix.core.Log;
import com.ameriglide.phenix.core.Optionals;
import jakarta.websocket.Session;
import net.inetalliance.types.Surnamed;
import net.inetalliance.types.json.JsonList;
import net.inetalliance.types.json.JsonMap;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

import static com.ameriglide.phenix.common.Opportunity.needsReminding;
import static com.ameriglide.phenix.core.Strings.isNotEmpty;
import static java.util.concurrent.TimeUnit.MINUTES;
import static net.inetalliance.potion.Locator.forEach;

public class ReminderHandler implements JsonMessageHandler, Runnable {

  private static final Log log = new Log();
  public static ReminderHandler $;
  private final Map<Integer, Set<JsonMap>> msgs;
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor((r) -> {
    var t = new Thread(r);
    t.setDaemon(true);
    return t;
  });
  private final Set<JsonMap> init = Collections.synchronizedSet(
    new TreeSet<>(Comparator.comparingInt(json -> json.getInteger("id"))));

  ReminderHandler() {
    $ = this;
    msgs = Collections.synchronizedMap(new HashMap<>());
    scheduler.scheduleWithFixedDelay(this, 0, 1, MINUTES);
  }

  @Override
  public void run() {
    final Map<TimeZone, Set<Ticket>> active = Events.getActiveAgents();
    msgs.clear();
    try {
      if (!active.isEmpty()) {
        active.forEach((timeZone, agents) -> forEach(needsReminding(15, MINUTES, timeZone).and(
          Opportunity.withAgentIdIn(agents.stream().map(t -> t.id).collect(Collectors.toSet()))), this::add));
        for (var entry : msgs.entrySet()) {
          final var value = entry.getValue();
          if (!value.isEmpty()) {
            Events.broadcast("reminder", entry.getKey(), new JsonList(value));
          }
        }
      }
    } catch (Throwable t) {
      log.error(t);
    }
  }

  private void add(Opportunity o) {
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
    msgs
      .computeIfAbsent(o.getAssignedTo().id, id -> init)
      .add(new JsonMap()
        .$("id", o.id)
        .$("reminder", o.getReminder())
        .$("heat", o.getHeat())
        .$("dial", dial)
        .$("contact", Optionals.of(o.getContact()).map(Surnamed::getFullName).orElse(""))
        .$("business", o.getBusiness().getAbbreviation())
        .$("productLine",
          JsonMap.$().$("name", o.getProductLine().getName()).$("abbreviation", o.getProductLine().getAbbreviation()))
        .$("amount", o.getAmount()));
  }  @Override
  public void onAsyncMessage(final List<Session> sessions, final JsonMap msg) {
    sessions.forEach(session -> onMessage(session, msg));
  }

  private static JsonMap label(final String label, final String phone) {
    return new JsonMap().$("label", label).$("phone", phone);
  }  @Override
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
      msgs.remove(ticket.id);
      forEach(needsReminding(15, MINUTES, ticket.getTimeZone()).and(Opportunity.withAgent(ticket.agent())), this::add);
      Events.broadcast("reminder", ticket.id, new JsonList(msgs.computeIfAbsent(ticket.id, i -> init)));
    }
  }






}
