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
import net.inetalliance.potion.Locator;
import net.inetalliance.potion.query.Query;
import net.inetalliance.potion.query.Search;
import net.inetalliance.types.geopolitical.Country;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonList;
import net.inetalliance.types.json.JsonMap;

import java.util.Comparator;
import java.util.regex.Pattern;

import static com.ameriglide.phenix.core.Strings.isNotEmpty;
import static com.ameriglide.phenix.types.CallDirection.OUTBOUND;
import static net.inetalliance.potion.Locator.forEach;


@WebServlet("/api/pop/*")
public class Pop extends TypeModel<Call> {

  public Pop() {
    super(Call.class, Pattern.compile("/api/pop/(.*)"));
  }

  protected static JsonMap toJson(Contact contact, Agent agent) {
    return toJson(contact, agent, new Path());
  }

  protected static JsonMap toJson(Contact contact, Agent agent, Path overallBest) {
    final JsonList list = new JsonList(1);
    var json = new JsonMap().$("id", contact.id).$("name", getContactLabel(contact)).$("leads", list);
    var matchQuality = matchQuality(agent);
    var contactBest = new Path(contact);
    forEach(Lead.withContact(contact), opp -> {
      if (matchQuality.compare(opp, overallBest.lead) > 0) {
        overallBest.lead = opp;
      }
      if (matchQuality.compare(opp, contactBest.lead) > 0) {
        contactBest.lead = opp;
      }
      var notes = new JsonList();
      forEach(Note.withLead(opp), n -> {
        var a = n.getAuthor();
        notes.add(new JsonMap()
          .$("id", n.id)
          .$("note", n.getNote())
          .$("author", a==null ? "Unknown":a.getFullName())
          .$("created", n.getCreated()));
      });
      var extra = new JsonMap();
      extra.$("productLine", new JsonMap().$("id", opp.getProductLine().id).$("name", opp.getProductLine().getName()));
      extra.$("assignedTo",
        new JsonMap().$("id", opp.getAssignedTo().id).$("name", opp.getAssignedTo().getFirstNameLastInitial()));
      extra.$("business", new JsonMap().$("id", opp.getBusiness().id).$("name", opp.getBusiness().getName()).$("uri",
        opp.getBusiness().getUri()));
      list.add(new JsonMap()
        .$("id", opp.id)
        .$("created", opp.getCreated())
        .$("notes", notes)
        .$("source", opp.getSource())
        .$("quote", opp.getQuote())
        .$("heat", opp.getHeat())
        .$("productLine", opp.getProductLine().id)
        .$("assignedTo", opp.getAssignedTo().id)
        .$("business", opp.getBusiness().id)
        .$("extra", extra));
    });
    return json.$("path", contactBest.toJson());
  }

  private static String getContactLabel(Contact c) {
    var f = c.getFirstName();
    var l = c.getLastName();
    if (Strings.isEmpty(l)) {
      return f;
    }
    return "%s %s".formatted(f, l);
  }

  private static Comparator<Lead> matchQuality(Agent loggedIn) {
    return (a, b) -> {
      if (a==null) {
        if (b==null) {
          return 0;
        }
        return -1;
      }
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

  @Override
  protected Json toJson(final Key<Call> key, final Call call, final HttpServletRequest request) {
    final Query<Contact> query;
    final var q = request.getParameter("q");
    final var phone = TaskRouter.toE164(call.getRemoteCaller().getPhone());
    var callContact = Optionals.of(call.getOpportunity()).map(Lead::getContact).orElseGet(call::getContact);
    if (isNotEmpty(q)) {
      query = new Search<>(Contact.class, getParameter(request, "n", 10), q.split(" "));
    } else if (callContact!=null) {
      query = Contact.withId(Contact.class, callContact.id);
    } else if (phone!=null && phone.length() >= 10) {
      query = Contact.withPhoneNumber(phone);
    } else {
      query = Query.none(Contact.class);
    }
    var contactId = request.getParameter("contact");
    var path = new Path();
    if (isNotEmpty(contactId) && !"new".equals(contactId)) {
      path.contact = Locator.$(new Contact(Integer.parseInt(contactId)));
    } else {
      path.contact = callContact;
    }
    var leadId = request.getParameter("lead");
    if (isNotEmpty(leadId) && !"new".equals(leadId)) {
      path.lead = Locator.$(new Lead(Integer.parseInt(leadId)));
    } else {
      path.lead = call.getOpportunity();
    }
    var loggedIn = Auth.getAgent(request);
    final var contacts = new JsonList(1);
    if (path.contact!=null) {
      contacts.add(toJson(path.contact, loggedIn, path));
    }
    var latest = call.getLastLeg();
    if(latest != null) {
      path.reservation = latest.sid.startsWith("WR") ? latest.sid : null;
    }
    var onlyPath = getParameter(request, "pathOnly", false);
    if (onlyPath && path.isComplete()) {
      return path.toJson();
    }
    forEach(query, c -> {
      if (path.contact==null || !path.contact.id.equals(c.id)) {
        contacts.add(toJson(c, loggedIn, path));
      }
    });
    if (onlyPath) {
      path.complete();
      return path.toJson();
    }

    var productLine = call.findProductLine();
    final Business biz = Optionals.of(call.getBusiness()).orElseGet(Business.getDefault);
    var contact = new JsonMap();
    var shipping = new JsonMap();
    contact.$("shipping", shipping);
    var lead = new JsonMap();
    var json = new JsonMap();

    json
      .$("defaults", new JsonMap().$("contact", contact).$("lead", lead))
      .$("direction", call.getDirection())
      .$("source", call.getSource())
      .$("business", new JsonMap().$("id", biz.id).$("name", biz.getName()))
      .$("contacts", contacts)
      .$("productLine", new JsonMap()
        .$("id", productLine.id)
        .$("abbreviation", productLine.getAbbreviation())
        .$("name", productLine.getName()));
    lead.$("heat", Heat.NEW);
    lead.$("business", biz.id);
    lead.$("productLine", productLine.id).$("source", call.getSource());

    if (phone!=null) {
      contact.$("phone", phone);
      if (call.getDirection()!=OUTBOUND) {
        contact.putAll(new FullName(call).toJson());
        Optionals
          .of(call.getState())
          .or(() -> Optionals.of(AreaCodeTime.getAreaCodeTime(phone)).map(AreaCodeTime::getUsState))
          .ifPresent(s -> shipping.$("state", s));
      }
      shipping.$("city", Strings.initialCapital(call.getCity()));
      shipping.$("country", Optionals.of(call.getCountry()).orElse(Country.UNITED_STATES));
      shipping.$("postalCode", call.getZip());
    }
    return json;
  }

  protected static class Path {
    Lead lead;
    Contact contact;
    String reservation;

    Path() {
    }

    public Path(final Contact contact) {
      this(contact, null);
    }

    public Path(final Contact contact, final Lead lead) {
      this.contact = contact;
      this.lead = lead;
    }

    boolean isComplete() {
      return contact!=null && lead!=null;
    }

    public Json toJson() {
      return JsonMap
        .$()
        .$("lead", lead==null ? "new":lead.id.toString())
        .$("contact", contact==null ? "new":contact.id.toString())
        .$("script", 1)
        .$("reservation",reservation);
    }

    public void complete() {
      if (contact==null && lead!=null) {
        contact = lead.getContact();
      }
    }
  }

}
