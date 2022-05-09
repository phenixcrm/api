package com.ameriglide.phenix.tasks;

import com.ameriglide.phenix.TaskRouter;
import com.ameriglide.phenix.common.Agent;
import com.twilio.rest.api.v2010.account.sip.credentiallist.CredentialDeleter;
import com.twilio.rest.taskrouter.v1.workspace.WorkerDeleter;
import net.inetalliance.log.Log;
import net.inetalliance.potion.Locator;

import static net.inetalliance.funky.StringFun.isNotEmpty;

public record PruneWorkers(TaskRouter router) implements Runnable {
  @Override
  public void run() {
    try {
      router.getWorkers().forEach(w-> {
        var a = Locator.$1(Agent.withTwilioSid(w.getSid()));
        if(a != null && !a.isActive()) {
          new WorkerDeleter(router.workspace.getSid(),w.getSid()).delete();
          if(isNotEmpty(a.getCredentialSid())) {
           new CredentialDeleter(router.sipCredentialList.getSid(),a.getCredentialSid()).delete();
          }
          Locator.update(a,"PruneWorkers",copy -> {
            copy.setTwilioSid(null);
            log.info("PruneWorkers %s -%s", a.getEmail(),w.getSid());
          });
        }
      });
    } catch(Throwable t) {
      log.error(t);
    }
  }
  private static final Log log = Log.getInstance(PruneWorkers.class);
}
