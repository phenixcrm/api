package com.ameriglide.phenix.api;

import com.ameriglide.phenix.servlet.Startup;
import com.ameriglide.phenix.common.Agent;
import com.ameriglide.phenix.common.Team;
import com.ameriglide.phenix.common.TeamMember;
import com.ameriglide.phenix.model.JsonCronServlet;
import jakarta.servlet.annotation.WebServlet;
import net.inetalliance.potion.query.Query;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonList;
import net.inetalliance.types.json.JsonMap;

import java.util.concurrent.TimeUnit;

import static net.inetalliance.potion.Locator.forEach;

@WebServlet("/api/hud")
public class Hud extends JsonCronServlet {
  public Hud() {
    super(10, TimeUnit.SECONDS);
  }

  @Override
  protected Json produce() {
    var json = new JsonMap();
    var teams = new JsonList();
    json.put("teams",teams);

    forEach(Query.all(Team.class), team -> {
      var teamJson = new JsonMap();
      teams.add(teamJson);
      teamJson.$("name",team.getName()).$("id",team.id);
      var members = new JsonList();
      teamJson.$("members", members);
      forEach(TeamMember.withTeam(team).and(Agent.isActive), agent -> members.add(new JsonMap()
        .$("id",agent.id)
        .$("firstName", agent.getFirstName())
        .$("lastName", agent.getLastName())
        .$("available", Startup.router.byAgent.getOrDefault(agent.getSid(),false))));
    });
    return json;
  }
}
