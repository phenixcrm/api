package com.ameriglide.phenix.api;

import com.ameriglide.phenix.Auth;
import com.ameriglide.phenix.common.Call;
import com.ameriglide.phenix.common.FullName;
import com.ameriglide.phenix.common.SkillQueue;
import com.ameriglide.phenix.core.Optionals;
import com.ameriglide.phenix.model.ListableModel;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import net.inetalliance.potion.query.Query;
import net.inetalliance.types.json.Json;

import java.util.regex.Pattern;

import static com.ameriglide.phenix.types.Resolution.*;
import static net.inetalliance.sql.OrderBy.Direction.DESCENDING;

@WebServlet("/api/missed")
public class MissedCallModel extends ListableModel<Call> {
  public MissedCallModel() {
    super(Call.class, Pattern.compile("/api/missed(?:/(.*))?"));
  }

  @Override
  public Query<Call> all(final Class<Call> type, final HttpServletRequest request) {
    var agent = Auth.getAgent(request);
    final Query<Call> base;
    var withVoicemail = Call
      .withResolution(VOICEMAIL)
      .or(Call.withResolution(ANSWERED).and(Call.withVoicemailRecording));
    if ("true".equals(request.getParameter("voicemail"))) {
      base = Call.withAgent(agent).and(withVoicemail);
    } else {
      base = Call.isQueue.and(withVoicemail.or(Call.withResolution(DROPPED)));
    }
    return Query.uncacheable(base.orderBy("created", DESCENDING));

  }

  @Override
  public Json toJson(final HttpServletRequest request, final Call call) {
    return new FullName(call)
      .toJson()
      .$("sid", call.sid)
      .$("direction", call.getDirection())
      .$("created", call.getCreated())
      .$("phone", call.getPhone())
      .$("state", call.getState())
      .$("todo", call.isTodo())
      .$("resolution", call.getResolution())
      .$("recordingSid", call.getRecordingSid())
      .$("voicemailSid", call.getVoicemailSid())
      .$("transcription", call.getTranscription())
      .$("duration", call.getDuration())
      .$("queue", Optionals.of(call.getQueue()).map(SkillQueue::getName).orElse(""));
  }

  @Override
  public int getPageSize(final HttpServletRequest request) {
    var sup = super.getPageSize(request);
    return sup==0 ? 20:sup;
  }
}
