package com.ameriglide.phenix.api;

import com.ameriglide.phenix.common.Call;
import com.ameriglide.phenix.common.Contact;
import com.ameriglide.phenix.common.Opportunity;
import com.ameriglide.phenix.exception.BadRequestException;
import com.ameriglide.phenix.exception.NotFoundException;
import com.ameriglide.phenix.model.Key;
import com.ameriglide.phenix.model.ListableModel;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import net.inetalliance.log.Log;
import net.inetalliance.potion.Locator;
import net.inetalliance.potion.query.Query;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;

import static net.inetalliance.funky.StringFun.isEmpty;
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
        final String cid =
                call.getCallerId() == null || isEmpty(call.getCallerId().getNumber()) ? null
                        : call.getCallerId().getNumber();
        if (call.getContact() != null) {
            q = Contact.withId(Contact.class, call.getContact().id);
        } else if (cid != null && cid.length() >= 10) {
            q = Contact.withPhoneNumber(call.getCallerId().getNumber());
        } else {
            q = Query.none(Contact.class);
        }
        return q;
    }

    @Override
    protected Json toJson(Key<Contact> key, Contact contact, HttpServletRequest request) {
        if (request.getParameter("lead") != null){
            return toJson(request, contact);
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
