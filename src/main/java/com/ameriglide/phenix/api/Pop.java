package com.ameriglide.phenix.api;

import com.ameriglide.phenix.Auth;
import com.ameriglide.phenix.common.*;
import com.ameriglide.phenix.model.Key;
import com.ameriglide.phenix.model.TypeModel;
import com.ameriglide.phenix.twilio.TaskRouter;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import net.inetalliance.funky.Funky;
import net.inetalliance.funky.StringFun;
import net.inetalliance.potion.query.Query;
import net.inetalliance.potion.query.Search;
import net.inetalliance.types.geopolitical.Country;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonList;
import net.inetalliance.types.json.JsonMap;

import java.util.Comparator;
import java.util.regex.Pattern;

import static net.inetalliance.funky.StringFun.titleCase;
import static net.inetalliance.potion.Locator.forEach;


@WebServlet("/api/pop/*")
public class Pop
  extends TypeModel<Call> {

  private static Comparator<Opportunity> matchQuality(Agent loggedIn) {
    return (a, b) -> {
      if (b == null) {
        return 1;
      }

      // then same agent opps
      if (!a.getAssignedTo().id.equals(b.getAssignedTo().id)) {
        if (loggedIn.id.equals(a.getAssignedTo().id)) {
          return 1;
        }
        if (loggedIn.id.equals(b.getAssignedTo().id)) {
          return -1;
        }
      }
      // then hot before cold
      return a.getHeat().compareTo(b.getHeat());
    };
  }


  public Pop() {
    super(Call.class, Pattern.compile("/api/pop/(.*)"));
  }

  @Override
  protected Json toJson(final Key<Call> key, final Call call, final HttpServletRequest request) {
    final Query<Contact> query;
    final var q = request.getParameter("q");
    final var phone = TaskRouter.toUS10(call.getPhone());
    if (StringFun.isNotEmpty(q)) {
      query = new Search<>(Contact.class, getParameter(request, "n", 10),
        q.split(" "));
    } else if (call.getContact() != null) {
      query = Contact.withId(Contact.class, call.getContact().id);
    } else if (phone != null && phone.length() >= 10) {
      query = Contact.withPhoneNumber(phone);
    } else {
      query = Query.none(Contact.class);
    }
    final Opportunity[] preferred = new Opportunity[1];
    var loggedIn = Auth.getAgent(request);
    final JsonList contacts = new JsonList(1);
    forEach(query, c -> {
      ContactJson e = toJson(c,loggedIn);
      contacts.add(e.json());
      if(e.preferred() != null) {
        preferred[0] = e.preferred();
      }
    });
    final ProductLine productLine =
      call.getQueue() == null ? null : call.getQueue().getProduct();
    final Business biz = call.getBusiness();

    final JsonMap map = new JsonMap().$("contacts", contacts)
      .$("business", new JsonMap().$("id", biz.id).$("name", biz.getName()))
      .$("path", new JsonMap().$("contact", preferred[0] == null
          ? "new"
          : preferred[0].getContact().id.toString())
        .$("lead", preferred[0] == null
          ? "new"
          : preferred[0].id.toString()));

    if (productLine != null) {
      map.$("productLine", new JsonMap()
        .$("id", productLine.id)
        .$("abbreviation", productLine.getAbbreviation())
        .$("name", productLine.getName()));
    }

    map.$("direction", call.getDirection());
    map.$("source", call.getSource());
    /* todo: implement referral tracking
    if (call.getSource() != null && call.getSource() == Source.REFERRAL) {
      final Queue queue = call.getQueue();
      if(queue != null) {
        final Affiliate affiliate = queue.getAffiliate();
        if(affiliate == null) {
          log.error("queue %s is referral, but has no affiliate", queue.key);

        } else {
          map.$("referrer", affiliate.getDomain());
        }
      }
    }
       */
    if (phone != null) {
      map.$("phone", phone);
      String[] split = call.getName().split("[ ,]", 2);
      map.$("lastName", titleCase(split[0]));
      if (split.length == 2) {
        map.$("firstName", titleCase(split[1]));
      }
      Funky.of(call.getState())
        .or(() -> Funky.of(AreaCodeTime.getAreaCodeTime(phone))
          .map(AreaCodeTime::getUsState))
        .ifPresent(s -> map.$("state", s));
    }
    map.$("city", StringFun.titleCase(call.getCity()));
    map.$("country", Funky.of(call.getCountry()).orElse(Country.UNITED_STATES));
    map.$("postalCode", call.getZip());
    return map;
  }

  record ContactJson(JsonMap json, Opportunity preferred) {
    void addPreferred() {
      if(preferred != null) {
        json.$("preferred", preferred.id);
      }
    }
  }

  protected static ContactJson toJson(Contact contact, Agent agent) {
    final JsonList list = new JsonList(1);
    var json = new JsonMap().$("id", contact.id).$("name", contact.getFullName()).$("leads", list);
    var preferred = new Opportunity[1];
    var matchQuality = matchQuality(agent);
    forEach(Opportunity.withContact(contact),
      opp -> {
        if (matchQuality.compare(opp, preferred[0]) > 0) {
          preferred[0] = opp;
        }
        list.add(new JsonMap()
          .$("id", opp.id)
          .$("source", opp.getSource())
          .$("heat", opp.getHeat())
          .$("productLine", new JsonMap()
            .$("id", opp.getProductLine().id)
            .$("name", opp.getProductLine().getName()))
          .$("agent", new JsonMap()
            .$("id", opp.getAssignedTo().id)
            .$("name", opp.getAssignedTo().getFirstNameLastInitial()))
          .$("business", new JsonMap().$("id", opp.getBusiness().id)
            .$("name", opp.getBusiness().getName())));
      });
    return new ContactJson(json, preferred[0]);
  }

}
