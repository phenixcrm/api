package com.ameriglide.phenix.tasks;

import com.ameriglide.phenix.TaskRouter;
import com.ameriglide.phenix.common.Agent;
import net.inetalliance.log.Log;
import net.inetalliance.potion.Locator;

import static net.inetalliance.funky.StringFun.isNotEmpty;
import static net.inetalliance.potion.Locator.update;

public record PruneWorkers(TaskRouter router) implements Runnable {
  @Override
  public void run() {
    try {
      router.getWorkers().forEach(w -> {
        var a = Locator.$1(Agent.withSid(w.getSid()));
        if (a != null && !a.isActive()) {
          if (router.delete(w)) {
            var credentialSid = a.getCredentialSid();
            if (isNotEmpty(credentialSid)) {
              if (router.deleteCredential(credentialSid)) {
                update(a, "PruneWorkers", copy -> {
                  copy.setCredentialSid(null);
                });
              } else {
                log.warning("Could not remove credential: %s", credentialSid);
              }
            }
            update(a, "PruneWorkers", copy -> {
              copy.setSid(null);
              log.info("PruneWorkers %s -%s", a.getEmail(), w.getSid());
            });
          } else {
            log.warning("Unable to remove worker: %s", w.getSid());
          }
        }
      });

    } catch (Throwable t) {
      log.error(t);
    }
  }

  private static final Log log = Log.getInstance(PruneWorkers.class);
}
