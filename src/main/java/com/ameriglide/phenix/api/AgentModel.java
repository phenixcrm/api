package com.ameriglide.phenix.api;

import com.ameriglide.phenix.Auth;
import com.ameriglide.phenix.common.Agent;
import com.ameriglide.phenix.model.ListableModel;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import net.inetalliance.potion.info.Info;
import net.inetalliance.potion.query.Query;
import net.inetalliance.types.json.Json;

import static net.inetalliance.sql.OrderBy.Direction.ASCENDING;

@WebServlet("/api/agent/*")
public class AgentModel extends ListableModel<Agent> {

  private final Info<Agent> info;

  public AgentModel() {
    super(Agent.class);
    this.info = Info.$(Agent.class);
  }

  @Override
  public Query<Agent> all(final Class<Agent> type, final HttpServletRequest request) {
    var loggedIn = Auth.getAgent(request);
    var query = Query.all(type);
    if (request.getParameter("visible") != null) {
      query = Agent.viewableBy(loggedIn);
    }
    if (request.getParameter("active") != null) {
      query = query.and(Agent.isActive);
    }
    return query.orderBy("lastName", ASCENDING).orderBy("firstName", ASCENDING);
  }

  @Override
  public Json toJson(final HttpServletRequest request, final Agent agent) {
    return info.toJson(agent)
        .$("lastNameFirstInitial", agent.getLastNameFirstInitial())
        .$("firstNameLastInitial", agent.getFirstNameLastInitial());
  }
}
