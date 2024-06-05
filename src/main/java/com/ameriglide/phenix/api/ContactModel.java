package com.ameriglide.phenix.api;

import com.ameriglide.phenix.Auth;
import com.ameriglide.phenix.common.Call;
import com.ameriglide.phenix.common.Contact;
import com.ameriglide.phenix.common.Lead;
import com.ameriglide.phenix.core.Log;
import com.ameriglide.phenix.model.Key;
import com.ameriglide.phenix.model.ListableModel;
import com.ameriglide.phenix.servlet.exception.BadRequestException;
import com.ameriglide.phenix.servlet.exception.NotFoundException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import net.inetalliance.potion.Locator;
import net.inetalliance.potion.query.Query;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.ameriglide.phenix.core.Strings.isEmpty;
import static com.ameriglide.phenix.core.Strings.isNotEmpty;
import static com.ameriglide.phenix.twilio.TaskRouter.toE164;
import static net.inetalliance.potion.Locator.$1;
import static net.inetalliance.sql.OrderBy.Direction.DESCENDING;

@WebServlet("/api/contact/*")
public class ContactModel extends ListableModel<Contact> {

  private static final Log log = new Log();

  public ContactModel() {
    super(Contact.class);
  }

  @Override
  protected JsonMap onError(final HttpServletRequest request, Key<Contact> key, JsonMap data, JsonMap errors) {
    var email = errors.get("email");
    var phone = errors.get("phone");
    var refer = new HashMap<String, Contact>();
    if (isNotEmpty(email) && email.startsWith("There is already")) {
      refer.put("email", Locator.$1(Contact.withEmail(data.get("email"))));
    } else if (isNotEmpty(phone) && phone.startsWith("There is already")) {
      refer.put("phone", Locator.$1(Contact.withPhoneNumber(data.get("phone"))));
    }
    if (!refer.isEmpty()) {
      var loggedIn = Auth.getAgent(request);
      return refer.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> {
        var path = new Pop.Path();
        path.contact = e.getValue();
        Pop.toJson(path.contact, loggedIn, path);
        return path.toJson();
      }, (a, b) -> {
        ((JsonMap) a).putAll((JsonMap) b);
        return a;
      }, JsonMap::new));
    }
    return errors;
  }

  @Override
  public Query<Contact> all(final Class<Contact> type, final HttpServletRequest request) {
    final String callId = request.getParameter("call");
    if (isEmpty(callId)) {
      throw new BadRequestException("can't query all contacts at once");
    }
    final Call call = Locator.$(new Call(callId));
    if (call==null) {
      throw new NotFoundException("Could not find call %s", callId);
    }
    final Query<Contact> q;
    if (call.getContact()!=null) {
      q = Contact.withId(Contact.class, call.getContact().id);
    } else if (call.hasPhone()) {
      q = Contact.withPhoneNumber(call.getPhone());
    } else {
      q = Query.none(Contact.class);
    }
    return q;
  }

  @Override
  protected Json toJson(Key<Contact> key, Contact contact, HttpServletRequest request) {
    if (request.getParameter("lead")!=null) {
      return toJson(request, contact);
    } else if (request.getParameter("pop")!=null) {
      var pop = Pop.toJson(contact, Auth.getAgent(request));
      pop.putAll(((JsonMap) super.toJson(key, contact, request)));
      return pop;
    }
    return super.toJson(key, contact, request);
  }

  @Override
  public Json toJson(final HttpServletRequest request, final Contact contact) {
    var summary = request.getParameter("summary")!=null;
    var lead = request.getParameter("lead")!=null;
    if (summary) {
      return summary(contact);
    } else if (lead) {
      return lead(contact);
    }
    var json = (JsonMap) super.toJson(request, contact);
    json.$("phone", toE164(contact.getPhone()));
    return json;
  }

  private static JsonMap summary(final Contact contact) {
    return new JsonMap().$("id", contact.id).$("name", contact.getFullName());
  }

  private static JsonMap lead(final Contact contact) {
    var lead = $1(Lead.withContact(contact).orderBy("created", DESCENDING));
    if (lead==null) {
      log.error(() -> "trying to lookup most recent lead for %d, but no leads found".formatted(contact.id));
      throw new NotFoundException();
    }
    return new JsonMap().$("id", contact.id).$("lead", lead.id);
  }
}
