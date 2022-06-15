package com.ameriglide.phenix.api;

import com.ameriglide.phenix.Auth;
import com.ameriglide.phenix.common.*;
import com.ameriglide.phenix.model.Key;
import com.ameriglide.phenix.model.TypeModel;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import net.inetalliance.funky.StringFun;
import net.inetalliance.log.Log;
import net.inetalliance.potion.Locator;
import net.inetalliance.potion.query.Query;
import net.inetalliance.potion.query.Search;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonList;
import net.inetalliance.types.json.JsonMap;

import java.util.Comparator;
import java.util.regex.Pattern;


@WebServlet("/api/pop/*")
public class Pop
  extends TypeModel<Call> {

  public Pop() {
    super(Call.class, Pattern.compile("/api/pop/(.*)"));
  }

  @Override
  protected Json toJson(final Key<Call> key, final Call call, final HttpServletRequest request) {
    final Query<Contact> query;
    final var q = request.getParameter("q");
    final var phone = Address.unformatPhoneNumber(call.getPhone());
    if (StringFun.isNotEmpty(q)) {
      final Search<Contact> search = new Search<>(Contact.class, getParameter(request, "n", 10),
        q.split(" "));
        query = search;
    } else if (call.getContact() != null) {
      query = Contact.withId(Contact.class, call.getContact().id);
    } else if (phone != null && phone.length() >= 10) {
      query = Contact.withPhoneNumber(phone);
    } else {
      query = Query.none(Contact.class);
    }
    final Opportunity[] preferred = new Opportunity[1];
    final JsonList contacts = new JsonList(1);
    final Agent loggedIn = Auth.getAgent(request);
    final Comparator<Opportunity> matchQuality = (a, b) -> {
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
      int stageCompare = a.getHeat().compareTo(b.getHeat());
      if (stageCompare != 0) {
        return stageCompare;
      }

      return 0;
    };
    Locator.forEach(query, contact -> {
      final JsonList list = new JsonList(1);
      contacts
        .add(new JsonMap().$("id", contact.id).$("name", contact.getFullName()).$("leads", list));
      Locator.forEach(Opportunity.withContact(contact) ,
        opp -> {
          if (matchQuality.compare(opp, preferred[0]) > 0) {
            preferred[0] = opp;
          }
          list.add(new JsonMap().$("id", opp.id)
            .$("source", opp.getSource())
            .$("heat", opp.getHeat())
            .$("productLine", new JsonMap().$("id", opp.getProductLine().id)
              .$("name", opp.getProductLine().getName()))
            .$("agent", new JsonMap().$("id", opp.getAssignedTo().id)
              .$("name", opp.getAssignedTo()
                .getFirstNameLastInitial()))
            .$("business", new JsonMap().$("id", opp.getBusiness().id)
              .$("name", opp.getBusiness().getName())));
        });
    });
    final ProductLine productLine =
      call.getQueue() == null ? null : call.getProductLine();
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
      map.$("productLine", new JsonMap().$("id", productLine.id).$("name", productLine.getName()));
    }

    map.$("direction", call.getDirection());
    map.$("source", call.getSource());
    if(call.getSource() != null && call.getSource() == Source.REFERRAL) {
      /* todo: implement referral tracking
      final Queue queue = call.getQueue();
      if(queue != null) {
        final Affiliate affiliate = queue.getAffiliate();
        if(affiliate == null) {
          log.error("queue %s is referral, but has no affiliate", queue.key);

        } else {
          map.$("referrer", affiliate.getDomain());
        }
      }

       */
    }
    if (phone != null) {
      map.$("phone", phone);
      String[] split = call.getName().split(" ", 2);
      map.$("firstName", split[0]);
      if (split.length == 2) {
        map.$("lastName", split[1]);
      }
      final AreaCodeTime areaCodeTime = AreaCodeTime.getAreaCodeTime(phone);
      if (areaCodeTime != null) {
        map.$("state", areaCodeTime.getUsState());
      }
    }
    return map;
  }
  private static final transient Log log = Log.getInstance(Pop.class);
}
