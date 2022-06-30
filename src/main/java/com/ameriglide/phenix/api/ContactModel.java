package com.ameriglide.phenix.api;

import com.ameriglide.phenix.Auth;
import com.ameriglide.phenix.common.Call;
import com.ameriglide.phenix.common.Contact;
import com.ameriglide.phenix.common.Opportunity;
import com.ameriglide.phenix.model.Key;
import com.ameriglide.phenix.model.ListableModel;
import com.ameriglide.phenix.servlet.exception.BadRequestException;
import com.ameriglide.phenix.servlet.exception.NotFoundException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import net.inetalliance.log.Log;
import net.inetalliance.potion.Locator;
import net.inetalliance.potion.query.Query;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;

import static net.inetalliance.funky.StringFun.isEmpty;
import static net.inetalliance.funky.StringFun.isNotEmpty;
import static net.inetalliance.potion.Locator.$1;
import static net.inetalliance.sql.OrderBy.Direction.DESCENDING;

@WebServlet("/api/contact/*")
public class ContactModel
        extends ListableModel<Contact> {

    public ContactModel() {
        super(Contact.class);
    }

    private static final Log log = Log.getInstance(ContactModel.class);

    private static JsonMap summary(final Contact contact) {
        return new JsonMap().$("id", contact.id).$("name", contact.getFullName());
    }

  @Override
  protected JsonMap onError(Key<Contact> key, JsonMap data, JsonMap errors) {
    var email = errors.get("email");
    var phone = errors.get("phone");
    if (isNotEmpty(email) && email.startsWith("There is already")) {
      errors.$("refer", Locator.$1(Contact.withEmail( data.get("email"))).id);
    } else if (isNotEmpty(phone) && phone.startsWith("There is already")){
      errors.$("refer", Locator.$1(Contact.withPhoneNumber(data.get("phone"))).id);
    }
    return errors;
  }

  private static JsonMap lead(final Contact contact) {
        var lead = $1(Opportunity
                .withContact(contact)
                .orderBy("created", DESCENDING));
        if (lead == null) {
            log.error("trying to lookup most recent lead for %d, but no leads found", contact.id);
            throw new NotFoundException();
        }
        return new JsonMap().$("id", contact.id).$("lead", lead.id);
    }

    @Override
    public Query<Contact> all(final Class<Contact> type, final HttpServletRequest request) {
        final String callId = request.getParameter("call");
        if (isEmpty(callId)) {
            throw new BadRequestException("can't query all contacts at once");
        }
        final Call call = Locator.$(new Call(callId));
        if (call == null) {
            throw new NotFoundException("Could not find call %s", callId);
        }
        final Query<Contact> q;
        if (call.getContact() != null) {
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
        if (request.getParameter("lead") != null){
            return toJson(request, contact);
        } else if(request.getParameter("pop") != null) {
          var pop = Pop.toJson(contact, Auth.getAgent(request));
          pop.addPreferred();
          var json = pop.json();
          json.putAll(((JsonMap)super.toJson(key,contact,request)));
          return json;
        }
        return super.toJson(key, contact, request);
    }

    @Override
    public Json toJson(final HttpServletRequest request, final Contact contact) {
        var summary = request.getParameter("summary") != null;
        var lead = request.getParameter("lead") != null;
        if (summary) {
            return summary(contact);
        } else if (lead) {
            return lead(contact);
        }
        return super.toJson(request, contact);
    }
}
