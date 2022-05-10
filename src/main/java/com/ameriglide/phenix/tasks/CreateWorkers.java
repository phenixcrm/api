package com.ameriglide.phenix.tasks;

import com.ameriglide.phenix.TaskRouter;
import com.ameriglide.phenix.common.Agent;
import net.inetalliance.log.Log;

import static net.inetalliance.potion.Locator.forEach;
import static net.inetalliance.potion.Locator.update;

public record CreateWorkers(TaskRouter router) implements Task {
  @Override
  public void exec() {
    try {
      forEach(Agent.disconnected, a -> {
        var w = router.createWorker(a);
        update(a, "ConnectWorkers", copy -> {
          copy.setTwilioSid(w.getSid());
          log.info("ConnectWorkers: %s -> %s", a.getEmail(), w.getSid());
        });
      });
    } catch(Throwable t) {
      log.error(t);
    }
  }
  private static final Log log = Log.getInstance(CreateWorkers.class);
}
