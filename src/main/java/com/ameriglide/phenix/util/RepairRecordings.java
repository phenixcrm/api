package com.ameriglide.phenix.util;

import com.ameriglide.phenix.common.Call;
import com.ameriglide.phenix.common.Leg;
import com.ameriglide.phenix.servlet.Startup;
import net.inetalliance.cli.Cli;
import net.inetalliance.potion.query.Query;
import net.inetalliance.sql.OrderBy;

import java.util.Objects;

import static net.inetalliance.potion.Locator.forEach;
import static net.inetalliance.potion.Locator.update;

public class RepairRecordings implements Runnable {
  public static void main(String[] args) {
    Startup.bootstrap();
    try {
      Cli.run(new RepairRecordings(), args);
    } finally {
      Startup.teardown();
    }
  }
  private static final com.ameriglide.phenix.core.Log log = new com.ameriglide.phenix.core.Log();

  @Override
  public void run() {
    forEach(Query.all(Call.class).orderBy("created", OrderBy.Direction.DESCENDING).limit(0,1000), call -> {
      var callRecording = Startup.router.getRecordingForCall(call.sid);
      var mismatch = callRecording!=null && !Objects.equals(callRecording.getSid(), call.getRecordingSid());
      if(mismatch) {
        log.info(()->"Call %s [%s] WRONG (%s)".formatted(call.sid, call.getRecordingSid(),callRecording.getSid()));
        update(call, "RepairRecordings", copy -> {
          copy.setRecordingSid(callRecording.getSid());
        });
        forEach(Leg.withCall(call), leg -> {
          var legRecording = Startup.router.getRecordingForCall(leg.sid);
          var mismatchedLeg = legRecording != null && !Objects.equals(leg.getRecordingSid(),legRecording.getSid());
          if(mismatchedLeg) {
            update(leg, "RepairRecordings", copy -> {
              copy.setRecordingSid(legRecording.getSid());
            });
            log.info(()->"Leg %s [%s] UPDATED".formatted(leg.sid, legRecording.getSid()));
          } else {
            log.info(()->"Leg %s [%s] MATCH".formatted(leg.sid, leg.getRecordingSid()));

          }
        });
      } else {
        log.info(()->"Call %s [%s] MATCH".formatted(call.sid, call.getRecordingSid()));
      }

    });
  }
}
