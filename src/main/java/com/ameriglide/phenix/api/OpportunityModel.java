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
import java.util.regex.Pattern;

import static com.ameriglide.phenix.core.Strings.isEmpty;
import static com.ameriglide.phenix.core.Strings.isNotEmpty;


@WebServlet("/api/opportunity/*")
public class OpportunityModel extends ListableModel<Lead> {

  private static final Pattern pattern = Pattern.compile("/(?:api|reporting)/opportunity(?:/(\\d+))?");
  public OpportunityModel() {
    super(Lead.class, pattern);
  }

  @Override
  public Query<Lead> all(final Class<Lead> type, final HttpServletRequest request) {
    Query<Lead> q = super.all(type, request);
    final var callId = request.getParameter("call");
    if (isNotEmpty(callId)) {
      final var call = Locator.$(new Call(callId));
      if (call==null) {
        throw new NotFoundException("Could not find call with key %s", callId);
      }
      q = q.and(Lead.withChannelIdIn(List.of(call.getChannel().id)));
    }
    final var contactId = request.getParameter("contact");
    if (isNotEmpty(contactId)) {
      final var contact = Locator.$(new Contact(Integer.valueOf(contactId)));
      if (contact==null) {
        throw new NotFoundException("Could not find contact with id %s", contactId);
      }
      q = q.and(Lead.withContact(contact));
    }

    return q;
  }

  @Override
  protected Lead lookup(final Key<Lead> key, final HttpServletRequest request) {
    if ("0".equals(key.id)) {
      final var lead = new Lead();
      lead.setHeat(Heat.QUOTED);
      lead.setAssignedTo(Auth.getAgent(request));
      final var callKey = request.getParameter("call");
      if (isEmpty(callKey)) {
        throw new BadRequestException("must specify a call key");
      }
      final var call = Locator.$(new Call(callKey));
      if (call==null) {
        throw new NotFoundException("could not find call with key %s", callKey);
      }
      lead.setProductLine(call.getDialedNumber().getProductLine());
      lead.setSource(call.getSource());
      lead.setCreated(LocalDateTime.now());
      lead.setBusiness(call.getChannel());
      return lead;
    }
    return super.lookup(key, request);
  }

  @Override
  public JsonMap create(final Key<Lead> key, final HttpServletRequest request,
                        final HttpServletResponse response, final JsonMap data) {
    data.put("created", LocalDateTime.now());
    return super.create(key, request, response, data);
  }

  @Override
  protected Json toJson(final Key<Lead> key, final Lead lead, final HttpServletRequest request) {
    final var map = (JsonMap) super.toJson(key, lead, request);
    map.put("extra", json(lead));
    return map;
  }

  public static Json json(Lead arg) {
    final Agent assignedTo = arg.getAssignedTo();
    final ProductLine productLine = arg.getProductLine();
    final Channel biz = arg.getBusiness();
    final Heat heat = arg.getHeat();
    return new JsonMap()
      .$("id", arg.id)
      .$("heat", new JsonMap().$("name", heat.name()).$("ordinal", heat.ordinal()))
      .$("business", new JsonMap()
        .$("id", biz.id)
        .$("abbreviation", biz.getAbbreviation())
        .$("uri", biz.getUri())
        .$("name", biz.getName()))
      .$("assignedTo", new JsonMap().$("name", assignedTo.getLastNameFirstInitial()).$("id", assignedTo.id))
      .$("productLine", new JsonMap()
        .$("name", productLine.getName())
        .$("abbreviation", productLine.getAbbreviation())
        .$("id", productLine.id)
        .$("root", productLine.getRoot()==null ? null:productLine.getRoot().id));
  }

  @Override
  public Json toJson(final HttpServletRequest request, final Lead o) {

    final var superJson = (JsonMap) super.toJson(request, o);
    if (request.getParameter("summary")==null) {
      return superJson;
    }
    return superJson.$("extra", json(o));
  }
}
