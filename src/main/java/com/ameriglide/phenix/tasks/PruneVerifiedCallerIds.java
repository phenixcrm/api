package com.ameriglide.phenix.tasks;

import com.ameriglide.phenix.TaskRouter;
import com.ameriglide.phenix.common.VerifiedCallerId;

import static java.util.stream.Collectors.toSet;
import static net.inetalliance.potion.Locator.delete;
import static net.inetalliance.potion.Locator.forEach;

public record PruneVerifiedCallerIds(TaskRouter router) implements Task {
  @Override
  public void exec() {
    var valid = router.getVerifiedCallerIds()
      .map(v->v.sid)
      .collect(toSet());
    forEach(VerifiedCallerId.withSidNotIn(valid), cid -> {
      delete("PruneVerifiedCallerIds", cid);
      log.debug("Removing invalid caller id: %s (%s -> %s)", cid.getFriendlyName(), cid.getPhoneNumber(), cid.sid);
    });
  }
}
