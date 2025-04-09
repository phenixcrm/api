package com.ameriglide.phenix.api;

import com.ameriglide.phenix.common.Call;
import com.ameriglide.phenix.common.Leg;
import com.ameriglide.phenix.common.Lead;
import com.ameriglide.phenix.core.Optionals;
import com.ameriglide.phenix.core.Strings;
import com.ameriglide.phenix.model.Listable;
import com.ameriglide.phenix.servlet.PhenixServlet;
import com.ameriglide.phenix.servlet.exception.BadRequestException;
import com.ameriglide.phenix.servlet.exception.NotFoundException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.potion.Locator;
import net.inetalliance.potion.info.Info;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonList;
import net.inetalliance.types.json.JsonMap;

import java.sql.SQLException;
import java.time.Duration;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.ameriglide.phenix.types.CallDirection.OUTBOUND;
import static net.inetalliance.potion.Locator.forEach;
import static net.inetalliance.sql.OrderBy.Direction.ASCENDING;

@WebServlet("/api/callHistory/*")
public class CallHistoryModel extends PhenixServlet {
  private static final Pattern id = Pattern.compile("/api/callHistory/(.*)");

  public CallHistoryModel() {
  }


  @Override
  protected void get(HttpServletRequest request, HttpServletResponse response) throws Exception {
    var lead = Strings
      .matcher(id)
      .apply(request.getRequestURI())
      .map(m -> m.group(1))
      .map(Integer::parseInt)
      .map(Lead::new)
      .map(Locator::$)
      .orElseThrow(() -> new BadRequestException("request must match /api/callHistory/(.*)"));
    if (lead==null) {
      throw new NotFoundException();
    }
    var contact = lead.getContact();
    var numbers = Call.getPhoneNumbers(contact);

    var params = numbers.stream().map(n -> "?").collect(Collectors.joining(","));
    var q = "SELECT Call.* FROM Call INNER JOIN Leg ON Leg.call=Call.sid "
      + "WHERE Call.contact=? OR (Call.direction = 'OUTBOUND' AND leg.phone in ("
      + params
      + ")) OR (Call.direction IN ('QUEUE','VIRTUAL','INBOUND') and call.phone in ("
      + params
      + "))";
    var calls = new JsonList();
    Locator.jdbc.executeQuery(q, stmt -> {
      try {
        stmt.setInt(1, contact.id);
        int i = 2;
        var n = numbers.size();
        for (String number : numbers) {
          stmt.setString(i, number);
          stmt.setString(i + n, number);
          i++;
        }
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }, resultSet -> Locator.read(Call.class, call -> calls.add(toJson(call))).apply(resultSet));
    respond(response, Listable.formatResult(calls));
  }


  public Json toJson(Call call) {
    new JsonMap();
    var json = JsonMap.$().$("sid").$("recordingSid").$("resolution").$("created").$("direction").$("transcription");
    Info.$(call).fill(call, json);
    Optionals
      .of(call.getChannel())
      .ifPresent(b -> json.$("channel", JsonMap.$().$("name", b.getName()).$("abbreviation", b.getAbbreviation())));
    json.$(call.getDirection()==OUTBOUND ? "to":"from", call.getRemoteCaller().toDisplayName());
    var recordings = new JsonList();
    json.$("recordings", recordings);
    if (Strings.isNotEmpty(call.getRecordingSid())) {
      recordings.add(call.getRecordingSid());
    }
    Locator.forEach(Leg.withCall(call).orderBy("created", ASCENDING), leg -> {
      if (Strings.isNotEmpty(leg.getRecordingSid())) {
        recordings.add(leg.getRecordingSid());
      }
    });
    Optionals.of(call.getAgent()).ifPresent(a -> json.$(call.getDirection()==OUTBOUND ? "from":"to", a.getFullName()));
    final JsonList talkList = new JsonList();
    json.put("talkTime", talkList);
    forEach(Leg.withCall(call).orderBy("created", ASCENDING), segment -> {
      final JsonMap talkMap = new JsonMap();
      if (segment.getAgent()!=null) {
        talkMap.put("agent", segment.getAgent().getLastNameFirstInitial());
      }
      if (segment.getAnswered()!=null && segment.getTalkTime()!=null) {
        talkMap.put("talkTime", format(segment.getTalkTime()));
      }
      talkList.add(talkMap);
    });
    return json;
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
    return !string.isEmpty() ? string.substring(0, string.length() - 1):"";

  }
}
