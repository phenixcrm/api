package com.ameriglide.phenix.api;

import com.ameriglide.phenix.common.Agent;
import com.ameriglide.phenix.common.Call;
import com.ameriglide.phenix.common.Team;
import com.ameriglide.phenix.common.TeamMember;
import com.ameriglide.phenix.core.Log;
import com.ameriglide.phenix.core.Optionals;
import com.ameriglide.phenix.model.JsonCronServlet;
import io.jsonwebtoken.lang.Objects;
import jakarta.servlet.annotation.WebServlet;
import net.inetalliance.potion.query.Query;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonList;
import net.inetalliance.types.json.JsonMap;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.ameriglide.phenix.Startup.hud;
import static net.inetalliance.potion.Locator.forEach;

@WebServlet("/api/hud")
public class Hud extends JsonCronServlet {


    public final static JsonMap map = new JsonMap(Collections.synchronizedMap(new HashMap<>()));
    public final static Map<Integer,JsonMap> byAgent = Collections.synchronizedMap(new HashMap<>());
    private static final Log log = new Log();

    public Hud() {
        super(2, TimeUnit.SECONDS);
    }

    public static JsonMap toStatusMap(Agent a, Call c) {
        return new JsonMap()
                .$("id",a.id)
                .$("firstName",a.getFirstName())
                .$("lastName",a.getLastName())
                .$("call", Optionals
                        .of(c)
                        .map(call -> JsonMap.$().$("sid", call.sid).$("direction", call.getDirection()))
                        .orElse(null));
    }

    @Override
    protected Json produce() {
        var json = new JsonMap();
        var teams = new JsonList();
        json.put("teams", teams);
        var calls = new HashMap<Integer, Call>();
        forEach(Call.isActiveVoiceCall, call -> call.getActiveAgents().forEach(a -> calls.put(a.id, call)));
        forEach(Agent.isActive, agent -> {
            byAgent.put(agent.id,toStatusMap(agent,calls.get(agent.id)));
        });
        forEach(Query.all(Team.class), team -> {
            var teamJson = new JsonMap();
            teams.add(teamJson);
            teamJson.$("name", team.getName()).$("id", team.id);
            var members = new JsonList();
            teamJson.$("members", members);
            forEach(TeamMember.withTeam(team).and(Agent.isActive), agent -> {
                members.add(byAgent.get(agent.id));
            });
        });
        map.clear();
        map.putAll(json);
        return json;
    }

    @Override
    protected void afterProduce(final String before, final String after) {
        super.afterProduce(before, after);
        if (!Objects.nullSafeEquals(before, after)) {
            log.info(() -> "HUD changed, broadcasting update");
            hud.changed(map);
        }
    }
}
