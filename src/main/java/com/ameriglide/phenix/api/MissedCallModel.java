package com.ameriglide.phenix.api;

import com.ameriglide.phenix.common.Call;
import com.ameriglide.phenix.common.FullName;
import com.ameriglide.phenix.common.SkillQueue;
import com.ameriglide.phenix.core.Optionals;
import com.ameriglide.phenix.model.ListableModel;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import net.inetalliance.potion.query.Query;
import net.inetalliance.types.json.Json;

import java.util.EnumSet;
import java.util.regex.Pattern;

import static com.ameriglide.phenix.types.CallDirection.QUEUE;
import static com.ameriglide.phenix.types.CallDirection.VIRTUAL;
import static com.ameriglide.phenix.types.Resolution.VOICEMAIL;
import static net.inetalliance.sql.OrderBy.Direction.DESCENDING;

@WebServlet("/api/missed")
public class MissedCallModel extends ListableModel<Call> {
  public MissedCallModel() {
    super(Call.class, Pattern.compile("/api/missed(?:/(.*))?"));
  }

  @Override
  public Query<Call> all(final Class<Call> type, final HttpServletRequest request) {
    return Call
      .withAgent(null)
      .and(Call.withDirectionIn(EnumSet.of(VIRTUAL, QUEUE)))
      .and(Call.withResolution(VOICEMAIL))
      .orderBy("created", DESCENDING);
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
      .$("resolution", call.getResolution())
      .$("recording", call.getRecordingSid())
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
