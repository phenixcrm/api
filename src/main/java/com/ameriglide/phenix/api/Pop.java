package com.ameriglide.phenix.api;

import com.ameriglide.phenix.Auth;
import com.ameriglide.phenix.common.*;
import com.ameriglide.phenix.core.Optionals;
import com.ameriglide.phenix.core.Strings;
import com.ameriglide.phenix.model.Key;
import com.ameriglide.phenix.model.TypeModel;
import com.ameriglide.phenix.twilio.TaskRouter;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import net.inetalliance.potion.query.Query;
import net.inetalliance.potion.query.Search;
import net.inetalliance.types.geopolitical.Country;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonList;
import net.inetalliance.types.json.JsonMap;

import java.util.Comparator;
import java.util.regex.Pattern;

import static com.ameriglide.phenix.core.Strings.initialCapital;
import static com.ameriglide.phenix.core.Strings.isNotEmpty;
import static com.ameriglide.phenix.types.CallDirection.OUTBOUND;
import static net.inetalliance.potion.Locator.forEach;


@WebServlet("/api/pop/*")
public class Pop extends TypeModel<Call> {

    public Pop() {
        super(Call.class, Pattern.compile("/api/pop/(.*)"));
    }

    @Override
    protected Json toJson(final Key<Call> key, final Call call, final HttpServletRequest request) {
        final Query<Contact> query;
        final var q = request.getParameter("q");
        final var phone = TaskRouter.toE164(call.getPhone());
        var contact = Optionals.of(call.getOpportunity()).map(Opportunity::getContact).orElseGet(call::getContact);
        if (isNotEmpty(q)) {
            query = new Search<>(Contact.class, getParameter(request, "n", 10), q.split(" "));
        } else if (contact!=null) {
            query = Contact.withId(Contact.class, contact.id);
        } else if (phone!=null && phone.length() >= 10) {
            query = Contact.withPhoneNumber(phone);
        } else {
            query = Query.none(Contact.class);
        }
        final Opportunity[] preferred = new Opportunity[1];
        var loggedIn = Auth.getAgent(request);
        final JsonList contacts = new JsonList(1);
        forEach(query, c -> {
            ContactJson e = toJson(c, loggedIn);
            contacts.add(e.json());
            if (e.preferred()!=null) {
                preferred[0] = e.preferred();
            }
        });

        final Business biz = Optionals.of(call.getBusiness()).orElseGet(Business.getDefault);

        final JsonMap map = new JsonMap().$("contacts", contacts).$("business", biz.id)

                .$("path", new JsonMap()
                        .$("contact", preferred[0]==null ? "new":preferred[0].getContact().id.toString())
                        .$("lead", preferred[0]==null ? "new":preferred[0].id.toString()));
        var productLine = call.findProductLine();
        map.$("productLine", productLine.id);

        map.$("direction", call.getDirection());
        map.$("source", call.getSource());

        map.$("extra", new JsonMap()
                .$("business", new JsonMap().$("id", biz.id).$("name", biz.getName()))
                .$("productLine", new JsonMap()
                        .$("id", productLine.id)
                        .$("abbreviation", productLine.getAbbreviation())
                        .$("name", productLine.getName())));
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
        if (phone!=null) {
            map.$("phone", phone);
            if (call.getDirection()!=OUTBOUND) {
                var name = call.getName();
                if (Strings.isNotEmpty(name)) {
                    String[] split = name.split("[ ,]", 2);
                    map.$("lastName", initialCapital(split[0]));
                    if (split.length==2) {
                        map.$("firstName", initialCapital(split[1]));
                    }
                }
                Optionals
                        .of(call.getState())
                        .or(() -> Optionals.of(AreaCodeTime.getAreaCodeTime(phone)).map(AreaCodeTime::getUsState))
                        .ifPresent(s -> map.$("state", s));
            }
            map.$("city", Strings.initialCapital(call.getCity()));
            map.$("country", Optionals.of(call.getCountry()).orElse(Country.UNITED_STATES));
            map.$("postalCode", call.getZip());
        }
        return map;
    }

    protected static ContactJson toJson(Contact contact, Agent agent) {
        final JsonList list = new JsonList(1);
        var json = new JsonMap().$("id", contact.id).$("name", contact.getFullName()).$("leads", list);
        var preferred = new Opportunity[1];
        var matchQuality = matchQuality(agent);
        forEach(Opportunity.withContact(contact), opp -> {
            if (matchQuality.compare(opp, preferred[0]) > 0) {
                preferred[0] = opp;
            }
            var notes = new JsonList();
            forEach(Note.withOpportunity(opp), n -> {
                var a = n.getAuthor();
                notes.add(new JsonMap()
                        .$("id", n.id)
                        .$("note", n.getNote())
                        .$("author", a==null ? "Unknown":a.getFullName())
                        .$("created", n.getCreated()));
            });
            var extra = new JsonMap();
            extra.$("productLine",
                    new JsonMap().$("id", opp.getProductLine().id).$("name", opp.getProductLine().getName()));
            extra.$("agent", new JsonMap()
                    .$("id", opp.getAssignedTo().id)
                    .$("name", opp.getAssignedTo().getFirstNameLastInitial()));
            extra.$("business", new JsonMap().$("id", opp.getBusiness().id).$("name", opp.getBusiness().getName()));

            list.add(new JsonMap()
                    .$("id", opp.id)
                    .$("notes", notes)
                    .$("source", opp.getSource())
                    .$("heat", opp.getHeat())
                    .$("productLine", opp.getProductLine().id)
                    .$("agent", opp.getAssignedTo().id)
                    .$("business", opp.getBusiness().id)
                    .$("extra", extra));
        });
        return new ContactJson(json, preferred[0]);
    }

    private static Comparator<Opportunity> matchQuality(Agent loggedIn) {
        return (a, b) -> {
            if (b==null) {
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

    record ContactJson(JsonMap json, Opportunity preferred) {
        void addPreferred() {
            if (preferred!=null) {
                json.$("preferred", preferred.id);
            }
        }
    }

}
