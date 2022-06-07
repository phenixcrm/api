package com.ameriglide.phenix.tasks;

import com.ameriglide.phenix.TaskRouter;
import com.ameriglide.phenix.common.Agent;

import java.util.Objects;

import static net.inetalliance.potion.Locator.$1;

public record SyncWorkerStatus(TaskRouter router) implements Task {
  @Override
  public void exec() {
    router.getWorkers().forEach(w -> {
      router.byAgent.put(w.getSid(), w.getAvailable());
      var a = $1(Agent.withTwilioSid(w.getSid()));
      if(a == null) {
        log.debug("Could not find agent for sid %s (%s)", w.getSid(),w.getFriendlyName());
      } else if(!Objects.equals(a.getSkills(),w.getAttributes())) {
        log.info("Updating skills for %s [%s]",a.getFullName(),a.getSkills());
        router.updateSkills(a);
      }
    });
  }
}
