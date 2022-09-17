package com.ameriglide.phenix.api;

import com.ameriglide.phenix.Auth;
import com.ameriglide.phenix.common.*;
import com.ameriglide.phenix.model.Key;
import com.ameriglide.phenix.model.ListableModel;
import com.ameriglide.phenix.servlet.exception.BadRequestException;
import com.ameriglide.phenix.servlet.exception.NotFoundException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.potion.Locator;
import net.inetalliance.potion.query.Query;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;

import java.time.LocalDateTime;
import java.util.List;

import static com.ameriglide.phenix.core.Strings.isEmpty;
import static com.ameriglide.phenix.core.Strings.isNotEmpty;


@WebServlet("/api/opportunity/*")
public class OpportunityModel
    extends ListableModel<Opportunity> {

  public OpportunityModel() {
    super(Opportunity.class);
  }

  public static Json json(Opportunity arg) {
    final Agent assignedTo = arg.getAssignedTo();
    final ProductLine productLine = arg.getProductLine();
    final Business biz = arg.getBusiness();
    final Heat heat = arg.getHeat();
    return new JsonMap().$("id", arg.id)
        .$("heat", new JsonMap().$("name", heat.name()).$("ordinal", heat.ordinal()))
        .$("business", new JsonMap().$("id", biz.id)
            .$("abbreviation", biz.getAbbreviation())
            .$("name", biz.getName()))
        .$("assignedTo",
            new JsonMap().$("name", assignedTo.getLastNameFirstInitial())
              .$("id", assignedTo.id))
        .$("productLine", new JsonMap().$("name", productLine.getName())
            .$("abbreviation", productLine.getAbbreviation())
            .$("id", productLine.id)
            .$("root", productLine.getRoot() == null
                ? null
                : productLine.getRoot().id));
  }

  @Override
  public JsonMap create(final Key<Opportunity> key, final HttpServletRequest request,
                        final HttpServletResponse response, final JsonMap data) {
    data.put("created", LocalDateTime.now());
    return super.create(key, request, response, data);
  }

  @Override
  public Query<Opportunity> all(final Class<Opportunity> type, final HttpServletRequest request) {
    Query<Opportunity> q = super.all(type, request);
    final var callId = request.getParameter("call");
    if (isNotEmpty(callId)) {
      final var call = Locator.$(new Call(callId));
      if (call == null) {
        throw new NotFoundException("Could not find call with key %s", callId);
      }
      q = q.and(Opportunity.withBusinessIdIn(List.of(call.getBusiness().id)));
    }
    final var contactId = request.getParameter("contact");
    if (isNotEmpty(contactId)) {
      final var contact = Locator.$(new Contact(Integer.valueOf(contactId)));
      if (contact == null) {
        throw new NotFoundException("Could not find contact with id %s", contactId);
      }
      q = q.and(Opportunity.withContact(contact));
    }

    return q;
  }

  @Override
  protected Opportunity lookup(final Key<Opportunity> key, final HttpServletRequest request) {
    if ("0".equals(key.id)) {
      final var opp = new Opportunity();
      opp.setHeat(Heat.QUOTED);
      opp.setAssignedTo(Auth.getAgent(request));
      final var callKey = request.getParameter("call");
      if (isEmpty(callKey)) {
        throw new BadRequestException("must specify a call key");
      }
      final var call = Locator.$(new Call(callKey));
      if (call == null) {
        throw new NotFoundException("could not find call with key %s", callKey);
      }
      opp.setProductLine(call.getQueue().getProduct());
      opp.setSource(call.getSource());
      opp.setCreated(LocalDateTime.now());
      opp.setBusiness(call.getBusiness());
      return opp;
    }
    return super.lookup(key, request);
  }

  @Override
  protected Json toJson(final Key<Opportunity> key, final Opportunity opportunity,
      final HttpServletRequest request) {
    final var map = (JsonMap) super.toJson(key, opportunity, request);
    map.put("extra", json(opportunity));
    return map;
  }

  @Override
  public Json toJson(final HttpServletRequest request, final Opportunity o) {

    final var superJson = (JsonMap) super.toJson(request, o);
    if (request.getParameter("summary") == null) {
      return superJson;
    }
    return superJson.$("extra", json(o));
  }
}
