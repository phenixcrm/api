package com.ameriglide.phenix.api;

import com.ameriglide.phenix.Auth;
import com.ameriglide.phenix.common.Agent;
import com.ameriglide.phenix.common.Call;
import com.ameriglide.phenix.common.FullName;
import com.ameriglide.phenix.model.ListableModel;
import com.ameriglide.phenix.servlet.exception.NotFoundException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.potion.Locator;
import net.inetalliance.potion.query.Query;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;

import java.util.regex.Pattern;

import static net.inetalliance.sql.OrderBy.Direction.DESCENDING;

@WebServlet("/api/digi/*")
public class DigiModel extends ListableModel<Call> {
  public DigiModel() {
    super(Call.class, Pattern.compile("/api/digi(?:/(.*)/take)?"));
  }

  @Override
  public Query<Call> all(final Class<Call> type, final HttpServletRequest request) {
    return Query.uncacheable(Call.withTask.and(Call.withAgent(Agent.system())).orderBy("created", DESCENDING));

  }

  @Override
  protected void put(final HttpServletRequest request, final HttpServletResponse response) throws Exception {
    var key = getKey(request);
    var call = Locator.$(new Call(key.id));
    if(call == null) {
      throw new NotFoundException("could not find call %s",key.id);
    }
    var loggedIn = Auth.getAgent(request);
    var existingAgent = call.getAgent();
    if(Agent.system().equals(existingAgent)) {
      Locator.update(call,"DigiModel",copy-> {
        copy.setAgent(loggedIn);
      });
      Locator.update(call.getOpportunity(),"DigiModel",copy-> {
        copy.setAssignedTo(loggedIn);
      });
      response.sendError(HttpServletResponse.SC_OK);
    } else {
      throw new NotFoundException();
    }
  }

  @Override
  public Json toJson(final HttpServletRequest request, final Call call) {
    return toJson(call);
  }

  public static JsonMap toJson(final Call call) {
    var opp = call.getOpportunity();
    var contact = opp.getContact();
    return new FullName(opp)
      .toJson()
      .$("sid", call.sid)
      .$("opportunity", opp.id)
      .$("contact", contact.id)
      .$("source", opp.getSource())
      .$("product", opp.getProductLine().getName())
      .$("created", call.getCreated())
      .$("phone", contact.getPhone())
      .$("state", contact.getState());
  }

  @Override
  public int getPageSize(final HttpServletRequest request) {
    var sup = super.getPageSize(request);
    return sup==0 ? 20:sup;
  }
}
