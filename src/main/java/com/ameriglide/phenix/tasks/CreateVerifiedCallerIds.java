package com.ameriglide.phenix.tasks;

import com.ameriglide.phenix.TaskRouter;
import com.ameriglide.phenix.common.VerifiedCallerId;
import net.inetalliance.potion.Locator;

import java.util.Objects;

import static net.inetalliance.potion.Locator.create;
import static net.inetalliance.potion.Locator.update;

public record CreateVerifiedCallerIds(TaskRouter router) implements Task {
  @Override
  public void exec() {
    router.getVerifiedCallerIds()
      .forEach(cid -> {
        var vCid = Locator.$(new VerifiedCallerId(cid.sid));
        if (vCid == null) {
          create("CreateVerifiedCallerIds", cid);
          log.debug("Added new verified caller id: %s (%s->%s)", cid.getFriendlyName(),
            cid.getPhoneNumber(), cid.sid);
        } else if(!Objects.equals(cid.getFriendlyName(), vCid.getFriendlyName())) {
          update(vCid,"CreateVerifiedCallerIds", copy -> {
            copy.setFriendlyName(cid.getFriendlyName());
          }) ;
        }
      });
  }
}
