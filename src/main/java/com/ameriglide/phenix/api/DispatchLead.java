package com.ameriglide.phenix.api;

import com.ameriglide.phenix.common.*;
import com.ameriglide.phenix.core.Log;
import com.ameriglide.phenix.core.Strings;
import com.ameriglide.phenix.servlet.PhenixServlet;
import com.ameriglide.phenix.servlet.exception.ForbiddenException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.potion.Locator;
import net.inetalliance.sql.Aggregate;
import net.inetalliance.sql.DateTimeInterval;
import net.inetalliance.types.Currency;
import net.inetalliance.types.geopolitical.Country;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonList;
import net.inetalliance.types.json.JsonMap;
import net.inetalliance.types.www.ContentType;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Optional;

import static com.ameriglide.phenix.twilio.TaskRouter.toE164;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;

@WebServlet("/api/dispatchLead")
public class DispatchLead extends PhenixServlet {
    private static final Log log = new Log();
    private static final String appId = "710e3aac-4ed8-49b0-b073-2b2e25905e55";
    private static final String tableName = "Phone%20Leads";

    public static void main(String[] args) {
        System.out.println(ZoneId.systemDefault().getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()));
    }

    @Override
    protected void post(final HttpServletRequest request, final HttpServletResponse response) {
        try {
            if ("4bca6a70-3cbf-45ea-9323-11b16e51047f".equals(request.getHeader("X-Api-Key"))) {
                var json = JsonMap.parse(request.getInputStream());
                var rawId = json.get("id");
                var submittedId = Strings.isEmpty(rawId) ? null:Integer.valueOf(rawId);
                if (submittedId==null) {
                    log.info(() -> "Call center dispatching lead: %s".formatted(Json.ugly(json)));
                    var phone = toE164(json.get("phone"));
                    var email = json.get("email");
                    var source = Source.valueOf(json.get("source").toUpperCase());
                    var contact = byEmail(email).orElseGet(() -> byPhone(phone).orElseGet(
                            () -> create(email, phone, source, json.get("firstName"), json.get("lastName"))));
                    var product = Locator.$1(ProductLine.withName(json.get("product")));
                    var reminder = json.getDateTime("reminder");
                    var opp = byProduct(contact, product).orElseGet(() -> create(contact, product, source, reminder));
                    var note = json.get("note");
                    if (Strings.isNotEmpty(note)) {
                        var n = new Note();
                        n.setOpportunity(opp);
                        n.setCreated(LocalDateTime.now());
                        n.setNote(note);
                        n.setAuthor(Agent.system());
                        log.debug(() -> "Added note for %d: %s".formatted(opp.id, note));
                        Locator.create("DispatchLead", n);
                    }
                    respond(response, new JsonMap().$("success", true));
                    notify(json.get("gid"), opp);
                }
            } else {
                throw new ForbiddenException();
            }
        } catch (Throwable t) {
            log.error(t);
        }
    }

    private Optional<Contact> byEmail(String email) {
        if (Strings.isNotEmpty(email)) {
            var contact = Locator.$1(Contact.withEmail(email));
            if (contact!=null) {
                log.info(() -> "Found existing contact for %s".formatted(email));
                return Optional.of(contact);
            }
        }
        return Optional.empty();
    }

    private Optional<Contact> byPhone(String phone) {
        if (Strings.isNotEmpty(phone)) {
            var contact = Locator.$1(Contact.withPhoneNumber(phone));
            if (contact!=null) {
                log.info(() -> "Found existing contact for %s".formatted(phone));
                return Optional.of(contact);
            }
        }
        return Optional.empty();

    }

    private Contact create(String email, String phone, Source source, String firstName, String lastName) {
        var contact = new Contact();
        contact.setEmail(email);
        contact.setPhone(phone);
        contact.setCreated(LocalDateTime.now());
        contact.setSource(source);
        contact.setFirstName(firstName);
        contact.setLastName(lastName);
        var areaCode = AreaCodeTime.getAreaCodeTime(phone);
        if (areaCode!=null) {
            var state = areaCode.getUsState();
            if (state!=null) {
                var address = new Address();
                address.setState(state);
                address.setCountry(Country.UNITED_STATES);
                contact.setShipping(address);
            } else {
                var province = areaCode.getProvince();
                if (province!=null) {
                    var address = new Address();
                    address.setCanadaDivision(province);
                    address.setCountry(Country.CANADA);
                    contact.setShipping(address);
                }
            }

        }
        log.info(() -> "Creating new contact %s<%s> %s [%s]".formatted(contact.getFullName(), email, phone, source));
        Locator.create("DispatchLead", contact);
        return contact;
    }

    private Optional<Opportunity> byProduct(Contact contact, ProductLine product) {
        var opp = Locator.$1(Opportunity.withContact(contact).and(Opportunity.withProductLine(product)));
        if (opp!=null) {
            log.info(
                    () -> "Found existing opportunity for %s [%s]".formatted(contact.getFullName(), product.getName()));
            return Optional.of(opp);
        }
        return Optional.empty();
    }

    private Opportunity create(Contact contact, ProductLine product, Source source, LocalDateTime reminder) {
        var opp = new Opportunity();
        opp.setContact(contact);
        opp.setBusiness(Business.getDefault.get());
        opp.setCreated(LocalDateTime.now());
        opp.setAssignedTo(Agent.system());
        opp.setSource(source);
        var now = LocalDateTime.now();
        var last30 = Locator.$$(Opportunity.soldInInterval(new DateTimeInterval(now.minusDays(30), now)), Aggregate.AVG,
                Currency.class, "amount");
        opp.setAmount(last30==null ? Currency.ZERO:last30);
        opp.setHeat(Heat.CONTACTED);
        opp.setReminder(reminder);
        opp.setProductLine(product);
        log.info(() -> "Created new opportunity for %s [%s]".formatted(contact.getFullName(), product.getName()));
        Locator.create("DispatchLead", opp);
        return opp;
    }

    private void notify(final String gid, final Opportunity opp) {
        var http = HttpClient.newHttpClient();
        var post = HttpRequest
                .newBuilder(URI.create(
                        "https://api.appsheet.com/api/v2/apps/%s/tables/%s/Edit".formatted(appId, tableName)))
                .POST(BodyPublishers.ofString(Json.ugly(new JsonMap()
                        .$("Action", "Edit")
                        .$("Properties", new JsonMap().$("Locale", "en-US").$("Timezone", "Eastern Standard Time"))
                        .$("Rows", JsonList.singleton(new JsonMap().$("gid", gid).$("id", opp.id))))))
                .setHeader("Content-type", ContentType.JSON.value)
                .setHeader("ApplicationAccessKey", "V2-y5XPV-ANE4L-bEUrG-bAwBd-ux2o2-ozptB-yrcOX-nNO1H")
                .build();
        try {
            var res = http.send(post, HttpResponse.BodyHandlers.ofString());
            var statusCode = res.statusCode();
            if (statusCode==SC_OK) {
                log.info("Updated AppSheet [%s]->%d", gid, opp.id);

            } else {
                log.error(() -> "Could not update AppSheet app [%d]: %s".formatted(statusCode, res.body()));
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
