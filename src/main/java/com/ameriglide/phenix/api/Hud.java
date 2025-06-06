package com.ameriglide.phenix.api;

import com.ameriglide.phenix.Startup;
import com.ameriglide.phenix.common.Agent;
import com.ameriglide.phenix.common.AgentStatus;
import com.ameriglide.phenix.common.Team;
import com.ameriglide.phenix.common.TeamMember;
import com.ameriglide.phenix.core.Optionals;
import com.ameriglide.phenix.servlet.PhenixServlet;
import com.ameriglide.phenix.servlet.topics.HudTopic;
import com.ameriglide.phenix.ws.SessionHandler;
import com.ameriglide.phenix.core.Log;
import io.jsonwebtoken.lang.Objects;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.potion.Locator;
import net.inetalliance.potion.query.Query;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonList;
import net.inetalliance.types.json.JsonMap;
import org.redisson.api.listener.MessageListener;

import static com.ameriglide.phenix.servlet.Startup.shared;
import static net.inetalliance.potion.Locator.forEach;

@WebServlet(value = "/api/hud", loadOnStartup = 1)
public class Hud extends PhenixServlet implements MessageListener<HudTopic> {

  private final JsonList teams;
  public JsonMap json;
  private String raw;
  private static final Log log = new Log();

  public Hud() {
    SessionHandler.hud = this;
    Startup.topics.hud().listen(this);
    this.teams = new JsonList();
    makeTeams();
    log.info(()->"HUD initialized, running first produce");
    new Thread(() -> {
      Startup.router.getWorkers().forEach(w -> {
        try {
          var agent = Locator.$1(Agent.withSid(w.getSid()));
          if (agent!=null) {
            shared.availability().put(agent.id, new AgentStatus(w, agent));
          }
        } catch(Throwable t) {
          log.error(t);
        }
      });
      produce();
    }).start();
  }

  private void makeTeams() {
    teams.clear();
    forEach(Query.all(Team.class).orderBy("name"), team -> {
      var membersList = new JsonList();
      var json = new JsonMap()
        .$("members", membersList)
        .$("name", team.getName())
        .$("position",team.getPosition())
        .$("queue",
          Optionals.of(team.getQueue()).map(q -> JsonMap.$().$("id", q.id).$("name", q.getName())).orElse(null));
      teams.add(json);
      forEach(TeamMember.withTeam(team), member -> {
        if (member.isActive()) {
          membersList.add((member.id));
        }
      });
    });

  }

  private void produce() {
    var agents = new JsonMap();
    forEach(Agent.connected, agent -> {
      agents.put(String.valueOf(agent.id),
        shared.availability().computeIfAbsent(agent.id, id -> new AgentStatus(agent)).toJson());
    });
    var newJson = new JsonMap().$("teams", teams).$("agents", agents);
    var newRaw = Json.ugly(newJson);
    if (!Objects.nullSafeEquals(raw, newRaw)) {
      raw = newRaw;
      json = newJson;
      SessionHandler.hudHandler.changed(newJson);
      log.info(()->"hud updated");

    } else {
      log.info(() -> "hud same");
    }

  }

  @Override
  public void onMessage(final CharSequence channel, final HudTopic msg) {
    switch (msg) {
      case PRODUCE -> produce();
      case TEAMS -> makeTeams();
    }
  }

  @Override
  protected void get(final HttpServletRequest request, final HttpServletResponse response) throws Exception {
    if (request.getParameter("produce")!=null) {
      produce();
    }
    if (request.getParameter("teams")!=null) {
      makeTeams();
    }
    respond(response, raw);
  }
}
