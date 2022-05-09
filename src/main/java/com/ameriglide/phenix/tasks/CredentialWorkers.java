package com.ameriglide.phenix.tasks;

import com.ameriglide.phenix.common.Agent;
import com.ameriglide.phenix.util.Security;
import com.twilio.rest.api.v2010.account.sip.credentiallist.CredentialCreator;
import net.inetalliance.log.Log;

import static net.inetalliance.potion.Locator.forEach;
import static net.inetalliance.potion.Locator.update;

public record CredentialWorkers(String credentialsListSid, Security security) implements Task {

  @Override
  public void exec() {
    try {
      forEach(Agent.connected.and(Agent.withoutSipSecret), a -> {
        log.info("Credentialing %s", a.getEmail());
        var secret = security.randomPassword();
        var credential =  new CredentialCreator(credentialsListSid, a.getSipUser(), secret).create();
        update(a, "CredentialWorkers", copy -> {
          copy.setCredentialSid(credential.getSid());
          copy.setSipSecret(security.encrypt(secret));
        });

      });
    } catch (Throwable t) {
      log.error(t);
    }

  }
  private static final Log log = Log.getInstance(CredentialWorkers.class);
}
