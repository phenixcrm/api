package com.ameriglide.phenix.api;

import com.ameriglide.phenix.Startup;
import com.ameriglide.phenix.common.*;
import com.ameriglide.phenix.servlet.PhenixServlet;
import com.ameriglide.phenix.servlet.exception.BadRequestException;
import com.ameriglide.phenix.twilio.TaskRouter;
import com.ameriglide.phenix.types.CallDirection;
import com.ameriglide.phenix.types.Resolution;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.funky.Funky;
import net.inetalliance.funky.StringFun;
import net.inetalliance.potion.Locator;
import net.inetalliance.sql.Aggregate;
import net.inetalliance.types.Currency;
import net.inetalliance.types.geopolitical.us.State;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;

import java.time.LocalDateTime;
import java.util.Locale;

import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static net.inetalliance.funky.StringFun.isNotEmpty;
import static net.inetalliance.funky.StringFun.titleCase;
import static net.inetalliance.potion.Locator.*;

@WebServlet("/api/createLead")
public class CreateLead extends PhenixServlet {
  void updateContact(final JsonMap data, Contact contact, final String phone, final String email) {
    contact.setPhone(phone);
    contact.setEmail(email);
    contact.setFirstName(titleCase(data.get("first")));
    contact.setLastName(titleCase(data.get("last")));
    var shipping = Funky.of(contact.getShipping()).orElseGet(()-> {
      contact.setShipping(new Address());
      return contact.getShipping();
    });
    shipping.setPostalCode(data.get("zip"));
    var state = data.get("State");
    if (isNotEmpty(state)) {
      shipping.setState(State.fromAbbreviation(state));
    }
  }

  @Override
  protected void post(final HttpServletRequest request, final HttpServletResponse response) throws Exception {
    var data = JsonMap.parse(request.getInputStream());
    var phone = TaskRouter.toUS10(data.get("mobile"));
    if (StringFun.isEmpty(phone)) {
      throw new BadRequestException("Must include 'mobile' phone");
    }
    var contact = $1(Contact.withPhoneNumber(phone));
    var email = data.get("email");
    if (contact == null && !StringFun.isEmpty(email)) {
      contact = $1(Contact.withEmail(email.toLowerCase(Locale.ROOT)));
    }
    if (contact == null) {
      contact = new Contact();
      updateContact(data,contact,phone,email);
      create("CreateLead",contact);
    } else {
      update(contact, "CreateLead", copy -> {
        updateContact(data,copy,phone,email);
      });
    }
    var product = Locator.$(new ProductLine(switch(Funky.of(data.getInteger("productLine")).orElse(0)) {
      case 0,1,10,25,10031,10035,10039 -> 17; // unassigned, adj bed, jewelry, walk-in tubs, patient lifts, shower
      // chairs, massage chairs -> undetermined
      case 2->6; // lift chairs
      case 3 -> 10; // scooters
      case 4,10030,10038 ->  8; // power, manual, specialty -> wheelchairs
      case 5 -> 5; // ramps
      case 6,10036,10037 -> 2; // stair lifts, stair climber, used -> stair lifts
      case 7 -> 1; // CVPLs
      case 8 -> 4; // dumbwaiters
      case  12 -> 9; // vehicle lifts
      case 13,15 -> 11; // bath lifts, toilet seat lifts -> bath lifts
      case 16,17,19,27,10032,10033 -> 12; // rolling walkers,med supply,accessories,parts,cushions,overlays ->
      // accessories
      case 18 -> 7; // elevators
      case 23 -> 14; // RVPLs
      case 26->15; // IVPLs
      case 10040 -> 3; // pool lifts
      case 10041 -> 16; // curved stair lifts
      default -> 17; // when all else fails, set to undetermined
    }));
    Opportunity opp;
    //todo add business support here
    var q = Locator.$1(SkillQueue.withProduct(product));
    if (contact.id == null) {
      create("CreateLead", contact);
      opp = null;
    } else {
      opp = Locator.$1(Opportunity.withContact(contact).and(Opportunity.withProductLine(product)));
    }
    if (opp == null) {
      opp = new Opportunity();
      opp.setContact(contact);
      opp.setAssignedTo(Agent.system());
      opp.setSource(Source.FORM);
      opp.setBusiness(q.getBusiness());
      opp.setHeat(Heat.HOT);
      opp.setProductLine(q.getProduct());
      opp.setAmount(Locator.$$(Opportunity.withProductLine(q.getProduct())
        .and(Opportunity.isSold), Aggregate.AVG, Currency.class, "amount"));
      create("CreateLead", opp);
    } else {
      var n = new Note();
      n.setOpportunity(opp);
      n.setAuthor(Agent.system());
      n.setCreated(LocalDateTime.now());
      n.setNote("Customer submitted web form");
      create("CreateLead", n);
    }
    var taskData = new JsonMap()
      .$("type", "sales")
      .$("product", product.getAbbreviation())
      .$("Lead", opp.id);
    if (opp.getAssignedTo() != null) {
      taskData.$("preferred", opp.getAssignedTo().getSid());
    }
    var task = Startup.router.createTask(Json.ugly(taskData));
    var call = new Call(task.getSid());
    call.setCreated(LocalDateTime.now());
    call.setDirection(CallDirection.VIRTUAL);
    call.setBusiness(q.getBusiness());
    call.setSource(Source.FORM);
    call.setResolution(Resolution.ACTIVE);
    call.setName("%s,%s".formatted(contact.getLastName(), contact.getFirstName()));
    call.setPhone(contact.getPhone());
    call.setCountry(contact.getShipping().getCountry());
    call.setQueue(q);
    call.setOpportunity(opp);
    call.setZip(contact.getShipping().getPostalCode());
    create("CreateLead", call);
    response.sendError(SC_OK);
  }

}
