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
import net.inetalliance.types.json.JsonMap;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static com.ameriglide.phenix.twilio.TaskRouter.toE164;

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
          var result = new AtomicReference<Result>();
          var contact = byEmail(result, email).orElseGet(() -> byPhone(result, phone).orElseGet(
            () -> create(result, email, phone, source, json.get("firstName"), json.get("lastName"))));
          final ProductLine product;
          if (json.containsKey("productLine")) {
            product = Locator.$(new ProductLine(json.getInteger("productLine")));
          } else {
            product = Locator.$1(ProductLine.withName(json.get("product")));
          }
          var reminder = json.getDateTime("reminder");
          var lead = byProduct(result, contact, product).orElseGet(
            () -> create(result, contact, product, source, reminder));
          var note = json.get("note");
          if (Strings.isNotEmpty(note)) {
            var n = new Note();
            n.setLead(lead);
            n.setCreated(LocalDateTime.now());
            n.setNote(note);
            n.setAuthor(Agent.system());
            log.debug(() -> "Added note for %d: %s".formatted(lead.id, note));
            Locator.create("DispatchLead", n);
          }
          respond(response, new JsonMap().$("result", result.get()));
          switch(result.get()) {
            case NEW_CONTACT,NEW_PRODUCT -> CreateLead.dispatch(lead);
          }
        }
      } else {
        throw new ForbiddenException();
      }
    } catch (Throwable t) {
      log.error(t);
    }
  }

  private Optional<Contact> byEmail(AtomicReference<Result> result, String email) {
    if (Strings.isNotEmpty(email)) {
      var contact = Locator.$1(Contact.withEmail(email));
      if (contact!=null) {
        log.info(() -> "Found existing contact for %s".formatted(email));
        result.set(Result.OLD_EMAIL);
        return Optional.of(contact);
      }
    }
    return Optional.empty();
  }

  private Optional<Contact> byPhone(AtomicReference<Result> result, String phone) {
    if (Strings.isNotEmpty(phone)) {
      var contact = Locator.$1(Contact.withPhoneNumber(phone));
      if (contact!=null) {
        log.info(() -> "Found existing contact for %s".formatted(phone));
        result.set(Result.OLD_PHONE);
        return Optional.of(contact);
      }
    }
    return Optional.empty();

  }

  private Contact create(AtomicReference<Result> result, String email, String phone, Source source, String firstName,
                         String lastName) {
    result.set(Result.NEW_CONTACT);
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

  private Optional<Lead> byProduct(AtomicReference<Result> result, Contact contact, ProductLine product) {
    var lead = Locator.$1(Lead.withContact(contact).and(Lead.withProductLine(product)));
    if (lead!=null) {
      log.info(() -> "Found existing lead for %s [%s]".formatted(contact.getFullName(), product.getName()));
      result.set(Result.ADD_NOTE);
      return Optional.of(lead);
    }
    return Optional.empty();
  }

  private Lead create(AtomicReference<Result> result, Contact contact, ProductLine product, Source source,
                      LocalDateTime reminder) {
    if (result.get()!=Result.NEW_CONTACT) {
      result.set(Result.NEW_PRODUCT);
    }
    var lead = new Lead();
    lead.setContact(contact);
    lead.setScreened(LocalDateTime.now());
    lead.setBusiness(Business.getDefault.get());
    lead.setCreated(LocalDateTime.now());
    lead.setAssignedTo(Agent.system());
    lead.setSource(source);
    var now = LocalDateTime.now();
    var last30 = Locator.$$(Lead.soldInInterval(new DateTimeInterval(now.minusDays(30), now)), Aggregate.AVG,
      Currency.class, "amount");
    lead.setAmount(last30==null ? Currency.ZERO:last30);
    lead.setHeat(Heat.CONTACTED);
    lead.setReminder(reminder);
    lead.setProductLine(product);
    log.info(() -> "Created new lead for %s [%s]".formatted(contact.getFullName(), product.getName()));
    Locator.create("DispatchLead", lead);
    return lead;
  }


  public enum Result {
    OLD_EMAIL, OLD_PHONE, NEW_CONTACT, NEW_PRODUCT, ADD_NOTE
  }

}
