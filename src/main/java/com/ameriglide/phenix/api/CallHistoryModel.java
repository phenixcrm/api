package com.ameriglide.phenix.api;

import com.ameriglide.phenix.common.Call;
import com.ameriglide.phenix.common.Leg;
import com.ameriglide.phenix.common.Opportunity;
import com.ameriglide.phenix.model.Listable;
import com.ameriglide.phenix.servlet.PhenixServlet;
import com.ameriglide.phenix.servlet.exception.BadRequestException;
import com.ameriglide.phenix.servlet.exception.NotFoundException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.funky.Funky;
import net.inetalliance.potion.Locator;
import net.inetalliance.potion.info.Info;
import net.inetalliance.potion.query.Query;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonList;
import net.inetalliance.types.json.JsonMap;

import java.time.Duration;
import java.util.regex.Pattern;

import static com.ameriglide.phenix.types.CallDirection.OUTBOUND;
import static net.inetalliance.potion.Locator.forEach;
import static net.inetalliance.sql.OrderBy.Direction.ASCENDING;
import static net.inetalliance.sql.OrderBy.Direction.DESCENDING;

@WebServlet("/api/callHistory/*")
public class CallHistoryModel extends PhenixServlet {
  private static final Pattern id = Pattern.compile("/api/callHistory/(.*)");

  public CallHistoryModel() {
  }


  @Override
  protected void get(HttpServletRequest request, HttpServletResponse response) throws Exception {
    var opp = Funky.matcher(id).apply(request.getRequestURI())
      .map(m -> m.group(1))
      .map(Integer::parseInt)
      .map(Opportunity::new)
      .map(Locator::$)
      .orElseThrow(() -> new BadRequestException("request must match /api/callHistory/(.*)"));
    if (opp == null) {
      throw new NotFoundException();
    }
    var contact = opp.getContact();
    respond(response, Listable.$(Call.class, new Listable<>() {
      @Override
      public Query<Call> all(Class<Call> type, HttpServletRequest request) {
        return Call.withContact(contact).orderBy("created", DESCENDING);
      }

      @Override
      public Json toJson(HttpServletRequest request, Call call) {
        new JsonMap();
        var json = JsonMap
          .$()
          .$("sid")
          .$("recordingSid")
          .$("resolution")
          .$("created")
          .$("direction")
          .$("transcription");
        Info.$(call).fill(call, json);
        Funky.of(call.getBusiness())
          .ifPresent(b -> json.$("business",
            JsonMap
              .$()
              .$("name", b.getName())
              .$("abbreviation", b.getAbbreviation())));
        json.$(call.getDirection() == OUTBOUND ? "to" : "from", call.getRemoteCaller().toDisplayName());
        Funky.of(call.getAgent())
          .ifPresent(a -> json.$(call.getDirection() == OUTBOUND ? "from" : "to", a.getFullName()));
        final JsonList talkList = new JsonList();
        json.put("talkTime", talkList);
        forEach(Leg.withCall(call).orderBy("created", ASCENDING), segment -> {
          final JsonMap talkMap = new JsonMap();
          if (segment.getAgent() != null) {
            talkMap.put("agent", segment.getAgent().getLastNameFirstInitial());
          }
          if (segment.getAnswered() != null && segment.getTalkTime() != null) {
            talkMap.put("talkTime", format(segment.getTalkTime()));
          }
          talkList.add(talkMap);
        });
        return json;
      }
    }, request));

  }

  private String format(long seconds) {
    var duration = Duration.ofSeconds(seconds);
    var string = new StringBuilder();
    var days = duration.toDays();
    if (days > 0) {
      string.append(days).append("duration ");
    }
    var h = duration.toHoursPart();
    if (h > 0) {
      string.append(h).append("h ");
    }
    var m = duration.toMinutesPart();
    if (m > 0) {
      string.append(m).append("m ");
    }
    var s = duration.toSecondsPart();
    if (s > 0) {
      string.append(s).append("s ");
    }
    return string.length() > 0 ? string.substring(0, string.length() - 1) : "";

  }
}
