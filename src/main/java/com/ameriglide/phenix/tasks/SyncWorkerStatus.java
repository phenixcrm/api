package com.ameriglide.phenix.tasks;

import com.ameriglide.phenix.TaskRouter;

public record SyncWorkerStatus(TaskRouter router) implements Task {
  @Override
  public void exec() {
    router.getWorkers().forEach(w -> router.byAgent.put(w.getSid(), w.getAvailable()));

  }
}
