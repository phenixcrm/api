package com.ameriglide.phenix.api;

import com.ameriglide.phenix.Startup;
import com.ameriglide.phenix.common.AgentStatus;
import com.ameriglide.phenix.common.Team;
import com.ameriglide.phenix.common.TeamMember;
import com.ameriglide.phenix.core.Log;
import com.ameriglide.phenix.servlet.PhenixServlet;
import com.ameriglide.phenix.ws.SessionHandler;
import io.jsonwebtoken.lang.Objects;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.potion.query.Query;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonList;
import net.inetalliance.types.json.JsonMap;

import java.util.stream.Collectors;

import static com.ameriglide.phenix.servlet.Startup.shared;
import static net.inetalliance.potion.Locator.forEach;

@WebServlet(value = "/api/hud", loadOnStartup = 1)
public class Hud extends PhenixServlet {

  private static final Log log = new Log();
  private final JsonList teams;
  public JsonMap json;
  private String raw;

  public Hud() {
    SessionHandler.hud = this;
    Startup.topics.hud().addListener(String.class, (channel, msg) -> {
      switch (msg.toUpperCase()) {
        case "PRODUCE" -> produce();
        case "TEAMS" -> makeTeams();
      }
    });
    this.teams = new JsonList();
    makeTeams();
    if (shared.availability().size()==0) {
      Startup.router.getWorkers().forEach(w -> {
        var status = new AgentStatus(w);
        shared.availability().put(status.id(), status);
      });
    }
    produce();
  }

  private void produce() {
    var agents = shared
      .availability()
      .entrySet()
      .stream()
      .collect(Collectors.toMap(e -> String.valueOf(e.getKey()), e -> e.getValue().toJson(), (a, b) -> {
        ((JsonMap) a).putAll((JsonMap) b);
        return a;
      }, JsonMap::new));
    var newJson = new JsonMap().$("teams", teams).$("agents", agents);
    var newRaw = Json.ugly(newJson);
    if (!Objects.nullSafeEquals(raw, newRaw)) {
      raw = newRaw;
      json = newJson;
    }

  }

  private void makeTeams() {
    teams.clear();
    forEach(Query.all(Team.class), team -> {
      var membersList = new JsonList();
      teams.add(membersList);
      forEach(TeamMember.withTeam(team), member -> membersList.add((member.id)));
    });

  }

  @Override
  protected void get(final HttpServletRequest request, final HttpServletResponse response) throws Exception {
    if (request.getParameter("produce")!=null) {
      produce();
    }
    respond(response, json);
  }
}
