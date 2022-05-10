package com.ameriglide.phenix.tasks;

import com.ameriglide.phenix.TaskRouter;
import com.ameriglide.phenix.common.Agent;
import com.twilio.rest.api.v2010.account.sip.credentiallist.Credential;
import net.inetalliance.log.Log;

import static net.inetalliance.potion.Locator.$1;
import static net.inetalliance.potion.Locator.update;

public record PruneCredentials(TaskRouter router) implements Runnable {
  @Override
  public void run() {
    try {
      router.getCredentials().map(Credential::getSid).forEach(sid -> {
        var a = $1(Agent.withCredentialSid(sid));
        if (a == null || !a.isActive()) {
          log.info("PruneCredentials %s -%s", a == null ? null : a.getEmail(), sid);
          if (router.deleteCredential(sid)) {
            update(a, "PruneCredentials", copy -> {
              copy.setCredentialSid(null);
            });
          } else {
            log.warning("Could not remove credential: %s", sid);
          }
        }
      });
    } catch(Throwable t) {
      log.error(t);
    }
  }
  private static final Log log = Log.getInstance(PruneCredentials.class);
}
