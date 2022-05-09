package com.ameriglide.phenix.tasks;

import com.ameriglide.phenix.TaskRouter;
import com.ameriglide.phenix.common.Agent;
import com.twilio.Twilio;
import com.twilio.rest.taskrouter.v1.workspace.Worker;
import net.inetalliance.log.Log;

import static net.inetalliance.potion.Locator.forEach;
import static net.inetalliance.potion.Locator.update;

public record CreateWorkers(TaskRouter router) implements Task {
  @Override
  public void exec() {
    try {
      forEach(Agent.disconnected, a -> {
        var w = Worker.creator(router.workspace.getSid(), a.getEmail()).create(Twilio.getRestClient());
        update(a, "ConnectWorkers", copy -> {
          copy.setTwilioSid(w.getSid());
          log.info("ConnectWorkers: %s -> %s", a.getEmail(), w.getSid());
        });
        Worker.updater(router.workspace.getSid(), w.getSid()).setActivitySid(router.offline.getSid());
      });
    } catch(Throwable t) {
      log.error(t);
    }
  }
  private static final Log log = Log.getInstance(CreateWorkers.class);
}
