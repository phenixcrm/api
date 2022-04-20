package com.ameriglide.phenix.api;

import com.ameriglide.phenix.common.Call;
import com.ameriglide.phenix.common.Contact;
import com.ameriglide.phenix.common.Opportunity;
import com.ameriglide.phenix.common.Segment;
import com.ameriglide.phenix.exception.MethodNotAllowedException;
import com.ameriglide.phenix.model.Key;
import com.ameriglide.phenix.model.Model;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.funky.Funky;
import net.inetalliance.potion.info.Info;
import net.inetalliance.potion.query.SortedQuery;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonList;
import net.inetalliance.types.json.JsonMap;

import java.time.Duration;
import java.util.regex.Matcher;

import static com.ameriglide.phenix.types.CallDirection.OUTBOUND;
import static java.util.regex.Pattern.compile;
import static net.inetalliance.potion.Locator.count;
import static net.inetalliance.potion.Locator.forEach;
import static net.inetalliance.sql.OrderBy.Direction.ASCENDING;
import static net.inetalliance.sql.OrderBy.Direction.DESCENDING;

@WebServlet("/api/callHistory/*")
public class CallHistoryModel
    extends Model<Opportunity> {

  public CallHistoryModel() {
    super(compile("/api/callHistory/(.*)"));
  }

  @Override
  protected void post(final HttpServletRequest request, final HttpServletResponse response) {
    throw new MethodNotAllowedException();
  }

  @Override
  protected void delete(final HttpServletRequest request, final HttpServletResponse response) {
    throw new MethodNotAllowedException();
  }

  @Override
  protected Key<Opportunity> getKey(final Matcher m) {
    return Key.$(Opportunity.class, m.group(1));
  }

  @Override
  protected void put(final HttpServletRequest request, final HttpServletResponse response) {
    throw new MethodNotAllowedException();
  }

  @Override
  protected Json toJson(final Key<Opportunity> key, final Opportunity opportunity,
      final HttpServletRequest request) {
    final Contact contact = opportunity.getContact();
    final SortedQuery<Call> callQuery = Call.withContact(contact).orderBy("created", DESCENDING);
    final JsonList calls = new JsonList(count(callQuery));
    forEach(callQuery, call -> {
      var callMap = new JsonMap();
      calls.add(callMap);
      callMap.$("key").$("notes").$("resolution").$("created").$("direction");
      Info.$(call).fill(call, callMap);
      Funky.of(call.getBusiness()).ifPresent(b->callMap.$("business",new JsonMap()
        .$("name",b.getName())
        .$("abbreviation",b.getAbbreviation())));
      callMap.put(call.getDirection() == OUTBOUND ? "to" : "from",
          call.getRemoteCallerId().getNumber());
      if (call.getAgent() != null) {
        callMap.put(call.getDirection() == OUTBOUND ? "from" : "to",
            call.getAgent().getLastNameFirstInitial());
      }
      final JsonList talkList = new JsonList();
      callMap.put("talkTime", talkList);
      forEach(Segment.withCall(call).orderBy("created", ASCENDING), segment -> {
        final JsonMap talkMap = new JsonMap();
        if (segment.getAgent() != null) {
          talkMap.put("agent", segment.getAgent().getLastNameFirstInitial());
        }
        if (segment.getAnswered() != null && segment.getTalkTime() != null) {
          talkMap.put("talkTime", format(segment.getTalkTime()));
        }
        talkList.add(talkMap);
      });
    });
    return calls;
  }
 private  String format(long seconds) {
    var duration = Duration.ofSeconds(seconds);
    var string = new StringBuilder();
    var days = duration.toDays();
    if(days>0) {
      string.append(days).append("duration ");
    }
    var h = duration.toHoursPart();
    if(h>0) {
      string.append(h).append("h ");
    }
    var m = duration.toMinutesPart();
    if(m>0) {
      string.append(m).append("m ");
    }
    var s = duration.toSecondsPart();
    if(s>0) {
      string.append(s).append("s ");
    }
    return string.length()>0 ? string.substring(0,string.length()-1) : "";

  }
}
