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
import net.inetalliance.types.struct.maps.LazyMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.ameriglide.phenix.common.Opportunity.needsReminding;
import static com.ameriglide.phenix.core.Strings.isNotEmpty;
import static java.util.concurrent.TimeUnit.MINUTES;
import static net.inetalliance.potion.Locator.forEach;

public class ReminderHandler
  implements JsonMessageHandler, Runnable {

  private static final Log log = new Log();
  public static ReminderHandler $;
  private final Map<Integer, JsonList> msgs;
  private final Lock lock;
  private final ScheduledExecutorService scheduler = Executors
    .newSingleThreadScheduledExecutor((r) -> {
      var t = new Thread(r);
      t.setDaemon(true);
      return t;
    });

  ReminderHandler() {
    $ = this;
    lock = new ReentrantLock();
    msgs = new LazyMap<>(new HashMap<>(8), s -> new JsonList());
    scheduler.scheduleWithFixedDelay(this, 0, 1, MINUTES);
  }

  private static JsonMap label(final String label, final String phone) {
    return new JsonMap().$("label", label).$("phone", phone);
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

  private void broadcast(Ticket ticket) {
    if (ticket != null) {
      lock.lock();
      try {
        msgs.remove(ticket.id());
        forEach(needsReminding(15, MINUTES).and(Opportunity.withAgent(ticket.agent())), this::add);
        Events.broadcast("reminder", ticket.id(), msgs.get(ticket.id()));
      } finally {
        lock.unlock();
      }
    }
  }

  private void add(Opportunity o) {
    final Contact c = o.getContact();
    final JsonList dial = new JsonList();
    final Address shipping = c.getShipping();
    if (shipping != null && isNotEmpty(shipping.getPhone())) {
      dial.add(label("Shipping", shipping.getPhone()));
    }
    final Address billing = c.getBilling();
    if (billing != null && isNotEmpty(billing.getPhone()) && (shipping == null || !Objects
      .equals(billing.getPhone(),
        (shipping.getPhone())))) {
      dial.add(label("Billing", billing.getPhone()));
    }
    msgs.get(o.getAssignedTo().id)
      .add(new JsonMap().$("id", o.id)
        .$("reminder", o.getReminder())
        .$("heat", o.getHeat())
        .$("dial", dial)
        .$("contact", Optionals.of(o.getContact()).map(Surnamed::getFullName).orElse(""))
        .$("business", o.getBusiness().getAbbreviation())
        .$("productLine", JsonMap.$()
          .$("name", o.getProductLine().getName())
          .$("abbreviation", o.getProductLine().getAbbreviation()))
        .$("amount", o.getAmount()));
  }

  @Override
  public void destroy() {
    scheduler.shutdownNow();
  }

  @Override
  public void run() {
    final Set<Integer> agents = Events.getActiveAgents();
    lock.lock();
    msgs.clear();
    try {
      if (!agents.isEmpty()) {
        forEach(needsReminding(15, MINUTES).and(
          Opportunity.withAgentIdIn(agents)), this::add);
        for (Map.Entry<Integer, JsonList> entry : msgs.entrySet()) {
          final JsonList value = entry.getValue();
          if (!value.isEmpty()) {
            Events.broadcast("reminder", entry.getKey(), value);
          }
        }
      }
    } catch (Throwable t) {
      log.error(t);
    } finally {
      lock.unlock();
    }
  }

}
