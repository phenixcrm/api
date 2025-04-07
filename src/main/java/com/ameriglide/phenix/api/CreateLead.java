package com.ameriglide.phenix.api;

import com.ameriglide.phenix.common.*;
import com.ameriglide.phenix.core.Log;
import com.ameriglide.phenix.core.Optionals;
import com.ameriglide.phenix.core.Strings;
import com.ameriglide.phenix.servlet.PhenixServlet;
import com.ameriglide.phenix.servlet.Startup;
import com.ameriglide.phenix.servlet.exception.BadRequestException;
import com.ameriglide.phenix.servlet.exception.NotFoundException;
import com.ameriglide.phenix.servlet.exception.UnauthorizedException;
import com.ameriglide.phenix.twilio.TaskRouter;
import com.ameriglide.phenix.types.CallDirection;
import com.ameriglide.phenix.types.Resolution;
import com.ameriglide.phenix.ws.SessionHandler;
import com.tupilabs.human_name_parser.HumanNameParserBuilder;
import com.tupilabs.human_name_parser.Name;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.potion.Locator;
import net.inetalliance.sql.Aggregate;
import net.inetalliance.types.Currency;
import net.inetalliance.types.geopolitical.us.State;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.ameriglide.phenix.common.Source.*;
import static com.ameriglide.phenix.core.Strings.isEmpty;
import static com.ameriglide.phenix.core.Strings.isNotEmpty;
import static com.ameriglide.phenix.servlet.Startup.router;
import static net.inetalliance.potion.Locator.*;

@WebServlet("/api/createLead")
public class CreateLead extends PhenixServlet {
  static final Pattern productFromCampaign = Pattern.compile(".* (.*) - .*");
  private static final Log log = new Log();
  public static final String WEBSITE_FORM_KEY = "b181cd58-2b94-4a25-8c5f-2008cfb5bd4a";

  public static void main(String[] args) {
    Startup.bootstrap();
    try {
      var lead = $1(Lead.withAgent($(Agent.system())).and(Lead.withSources(Set.of(FORM, SOCIAL))));
      lead.setProductLine(Locator.$(new ProductLine(13)));
      dispatch(lead);
      log.info(() -> "Dispatched Opp %d".formatted(lead.id));
    } finally {
      Startup.teardown();
    }
  }

  @Override
  protected void post(final HttpServletRequest request, final HttpServletResponse response) throws Exception {
    var leadgen = getLeadGenSource(request);
    if (leadgen==null && !WEBSITE_FORM_KEY.equals(request.getHeader("x-api-key"))) {
      throw new UnauthorizedException();
    }
    var data = JsonMap.parse(request.getInputStream());
    log.info("CreateLead: %s", Json.ugly(data));
    var phone = Stream
      .of("mobile", "phone")
      .map(data::get)
      .map(TaskRouter::toE164)
      .filter(Strings::isNotEmpty)
      .findFirst()
      .orElseThrow(() -> new BadRequestException("You must provide either 'mobile' or 'phone'"));
    var contact = $1(Contact.withPhoneNumber(phone));
    var email = data.get("email");
    if (contact==null && !isEmpty(email)) {
      contact = $1(Contact.withEmail(email.toLowerCase(Locale.ROOT)));
    }
    if (contact==null) {
      contact = new Contact();
      updateContact(data, contact, phone, email);
      create("CreateLead", contact);
    } else {
      update(contact, "CreateLead", copy -> {
        updateContact(data, copy, phone, email);
      });
    }
    Channel channel = null;
    var campaign = data.get("campaign");
    final Integer productId;
    if (isNotEmpty(campaign)) {
      var m = productFromCampaign.matcher(campaign);
      if (m.matches()) {
        productId = switch (m.group(1).toUpperCase(Locale.ROOT)) {
          case "SL" -> 6;
          case "LC" -> 2;
          case "VPL" -> 23;
          default -> null;
        };
      } else {
        log.warn(() -> "weird FB campaign name %s".formatted(campaign));
        productId = null;
      }
      if (campaign.endsWith("Ontario") || campaign.endsWith("GTA")) {
        channel = $(new Channel(2));
      }
    } else if (leadgen!=null) {
      productId = leadgen.getProductLine().id;
    } else {
      productId = null;
    }
    var product = Locator.$(new ProductLine(
      switch (Stream.of(productId, data.getInteger("productLine")).filter(Objects::nonNull).findFirst().orElse(0)) {
        case 0, 1, 10, 25, 10031, 10035, 10039 ->
          17; // unassigned, adj bed, jewelry, walk-in tubs, patient lifts, shower
        // chairs, massage chairs -> undetermined
        case 2 -> 6; // lift chairs
        case 3 -> 10; // scooters
        case 4, 10030, 10038 -> 8; // power, manual, specialty -> wheelchairs
        case 5 -> 5; // ramps
        case 6, 10036, 10037 -> 2; // stair lifts, stair climber, used -> stair lifts
        case 8 -> 4; // dumbwaiters
        case 7, 23 -> 1; // VPLs
        case 12 -> 9; // vehicle lifts
        case 13, 15 -> 11; // bath lifts, toilet seat lifts -> bath lifts
        case 16, 17, 19, 27, 10032, 10033 -> 12; // rolling walkers,med supply,accessories,parts,cushions,overlays ->
        // accessories
        case 18 -> 7; // elevators
        case 26 -> 15; // IVPLs
        case 10040 -> 3; // pool lifts
        case 10041 -> 16; // curved stair lifts
        case 69 -> 13; // sprockets
        default -> 17; // when all else fails, set to undetermined
      }));
    Lead lead;
    if (contact.id==null) {
      create("CreateLead", contact);
      lead = null;
    } else {
      lead = $1(Lead.withContact(contact).and(Lead.withProductLine(product)));
    }
    var note = new StringBuilder();
    var rawNote = data.get("note");
    if (Strings.isNotEmpty(rawNote)) {
      note.append(rawNote);
    }
    var extra = data.getMap("extra");
    if (extra!=null && !extra.isEmpty()) {
      if (!note.isEmpty()) {
        note.append('\n');
      }
      note.append(extra
        .entrySet()
        .stream()
        .map(e -> "%s=%s".formatted(e.getKey(), e.getValue().toString()))
        .collect(Collectors.joining("\n")));
    }
    var source = leadgen!=null ? REFERRAL:(isNotEmpty(campaign) ? SOCIAL:FORM);
    if (lead==null) {
      lead = new Lead();
      lead.setSource(source);
      lead.setContact(contact);
      lead.setAssignedTo(Agent.system());
      lead.setChannel(channel);
      if (leadgen!=null) {
        lead.setCampaign(leadgen);
        lead.setReferrerId(data.get("referrerId"));
        lead.setAssignedTo(Agent.system());
      }
      if (channel==null) {
        lead.setChannel(Channel.getDefault.get());
      }
      lead.setHeat(Heat.NEW);
      lead.setProductLine(product);
      lead.setAmount(Optionals
        .of(Locator.$$(Lead.withProductLine(product).and(Lead.isSold), Aggregate.AVG, Currency.class, "amount"))
        .orElse(Currency.ZERO));
      create("CreateLead", lead);
    } else {
      if (lead.getSource()==null) {
        Locator.update(lead, "CreateLead", copy -> {
          copy.setSource(source);
        });
      }

    }
    var n = new Note();
    n.setLead(lead);
    n.setAuthor(Agent.system());
    n.setCreated(LocalDateTime.now());
    n.setNote("Customer submitted web form: " + (Strings.isEmpty(note) ? "":note));
    create("CreateLead", n);
    dispatch(lead);
    respond(response, new JsonMap().$("lead", lead.id));
  }

  private Campaign getLeadGenSource(HttpServletRequest req) {
    var apiKey = req.getHeader("x-api-key");
    if (isNotEmpty(apiKey)) {
      if (WEBSITE_FORM_KEY.equals(apiKey)) {
        return null;
      }
      var leadGen = Locator.$1(LeadGen.withApiKey(apiKey));
      if (leadGen==null) {
        throw new NotFoundException("no partner with the specified key");
      }
      var campaignKey = req.getHeader("x-campaign-key");
      if (isEmpty(campaignKey)) {
        throw new BadRequestException("you must provide x-campaign-key as a header");
      }
      var campaign = Locator.$1(Campaign.withLeadGenCampaign(campaignKey));
      if (campaign==null) {
        throw new NotFoundException("no campaign with the specified key");
      }
      if (!leadGen.equals(campaign.getSource())) {
        log.error(() -> "somehow a lead with campaign %s was submitted by the wrong lead gen %d, expected %d".formatted(
          campaign.id, campaign.getSource().id, leadGen.id));
        throw new BadRequestException("please contact your representative");
      }
      return campaign;
    }
    return null;
  }

  void updateContact(final JsonMap data, Contact contact, final String phone, final String email) {
    contact.setPhone(phone);
    contact.setEmail(email);
    if(data.containsKey("fullName")) {
      var name = new Name(data.get("fullName"));
      var builder = new HumanNameParserBuilder(name);
      var parser = builder.build();
      contact.setFirstName(parser.getFirst());
      contact.setLastName(parser.getLast());
    } else {
      contact.setFirstName(Strings.titlecase(data.get("first")));
      contact.setLastName(Strings.titlecase(data.get("last")));
    }
    var shipping = Optionals.of(contact.getShipping()).orElseGet(() -> {
      contact.setShipping(new Address());
      return contact.getShipping();
    });
    shipping.setPostalCode(data.get("zip"));
    var state = data.get("State");
    if (isEmpty(state)) {
      state = data.get("state");
    }
    if (isNotEmpty(state)) {
      shipping.setState(State.fromAbbreviation(state));
    }
    var city = data.get("city");
    if (isNotEmpty(city)) {
      shipping.setCity(city);
    }
    if (shipping.getState()==null && Strings.isNotEmpty(shipping.getPostalCode())) {
      shipping.setState(State.fromZipCode(shipping.getPostalCode()));
    }
  }

  public static Call dispatch(Lead lead) {
    var product = lead.getProductLine();
    var contact = lead.getContact();
    var taskData = new JsonMap().$("type", "leadScreening").$("product", product.getAbbreviation()).$("Lead", lead.id);
    if (lead.getSource()==PHONE && lead.getAssignedTo()!=null) {
      taskData.$("preferred", lead.getAssignedTo().getSid());
    }
    taskData.$("channel", lead.getChannel().getAbbreviation());
    var task = router.createDigitalLeadsTask(Json.ugly(taskData), (int) TimeUnit.DAYS.toSeconds(1));
    var call = new Call(task.getSid());
    call.setCreated(LocalDateTime.now());
    call.setDirection(CallDirection.VIRTUAL);
    call.setChannel(lead.getChannel());
    call.setSource(lead.getSource());
    call.setAgent(Agent.system());
    call.setResolution(Resolution.ACTIVE);
    call.setName("%s,%s".formatted(contact.getLastName(), contact.getFirstName()));
    call.setPhone(contact.getPhone());
    call.setCountry(contact.getShipping().getCountry());
    call.setQueue(router.getQueue("leadScreening"));
    call.setOpportunity(lead);
    call.setContact(contact);
    call.setZip(contact.getShipping().getPostalCode());
    create("CreateLead", call);
    if (SessionHandler.digiHandler!=null) {
      try {
        SessionHandler.digiHandler.newDigi(DigiModel.toJson(call));
      } catch (Throwable t) {
        log.error(t);
      }
    }
    return call;
  }

}
