package com.ameriglide.phenix.tasks;

import com.ameriglide.phenix.TaskRouter;
import com.ameriglide.phenix.common.Agent;
import com.ameriglide.phenix.util.Security;
import net.inetalliance.log.Log;

import static net.inetalliance.potion.Locator.forEach;
import static net.inetalliance.potion.Locator.update;

public record CredentialWorkers(TaskRouter router, Security security) implements Task {

  @Override
  public void exec() {
    try {
      forEach(Agent.connected.and(Agent.withoutSipSecret), this::credential);
    } catch (Throwable t) {
      log.error(t);
    }

  }
  private static final Log log = Log.getInstance(CredentialWorkers.class);
  public void credential(final Agent a) {
    log.info("Credentialing %s", a.getEmail());
    var secret = security.randomPassword();
    var credential =
      router.createCredentials(a.getSipUser(), secret);
    update(a, "CredentialWorkers", copy -> {
      copy.setCredentialSid(credential.getSid());
      copy.setSipSecret(security.encrypt(secret));
    });

  }
}
