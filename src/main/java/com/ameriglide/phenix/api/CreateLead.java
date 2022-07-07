package com.ameriglide.phenix.api;

import com.ameriglide.phenix.Startup;
import com.ameriglide.phenix.common.*;
import com.ameriglide.phenix.servlet.PhenixServlet;
import com.ameriglide.phenix.servlet.exception.BadRequestException;
import com.ameriglide.phenix.twilio.TaskRouter;
import com.ameriglide.phenix.types.CallDirection;
import com.ameriglide.phenix.types.Resolution;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.funky.StringFun;
import net.inetalliance.log.Log;
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
import static net.inetalliance.potion.Locator.$;
import static net.inetalliance.potion.Locator.$1;

public class CreateLead extends PhenixServlet {

  private Opportunity newOpp(final Contact c, final SkillQueue q) {
    var opp = new Opportunity();
    opp.setContact(c);
    opp.setBusiness(q.getBusiness());
    opp.setHeat(Heat.HOT);
    opp.setProductLine(q.getProduct());
    opp.setAmount(Locator.$$(Opportunity.withProductLine(q.getProduct())
      .and(Opportunity.isSold), Aggregate.AVG, Currency.class, "amount"));
    Locator.create("CreateLead", opp);
    return opp;
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
    }
    contact.setPhone(phone);
    contact.setEmail(email);
    contact.setFirstName(titleCase(data.get("first")));
    contact.setLastName(titleCase(data.get("last")));
    contact.getShipping().setPostalCode(data.get("zip"));
    var state = data.get("State");
    if (isNotEmpty(state)) {
      contact.getShipping().setState(State.fromAbbreviation(state));
    }
    var product = $(new ProductLine(data.getInteger("productLine")));
    Opportunity opp;
    //todo add business support here
    var q = Locator.$1(SkillQueue.withProduct(product));
    if (contact.id == null) {
      Locator.create("CreateLead", contact);
      opp = null;
    } else {
      opp = Locator.$1(Opportunity.withContact(contact).and(Opportunity.withProductLine(product)));
    }
    if (opp == null) {
      opp = newOpp(contact, q);
    } else {
      var n = new Note();
      n.setOpportunity(opp);
      n.setAuthor(Agent.system());
      n.setCreated(LocalDateTime.now());
      n.setNote("Customer submitted web form");
      Locator.create("CreateLead", n);
    }
    var taskData = new JsonMap()
      .$("type", "sales")
      .$("product", product.getAbbreviation())
      .$("Opportunity", opp.id);
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
    Locator.create("CreateLead", call);
    response.sendError(SC_OK);
  }

  private static final Log log = Log.getInstance(CreateLead.class);
}
