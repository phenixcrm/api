package com.ameriglide.phenix.tasks;

import com.ameriglide.phenix.TaskRouter;
import com.ameriglide.phenix.common.SkillQueue;
import net.inetalliance.log.Log;

import static net.inetalliance.potion.Locator.forEach;
import static net.inetalliance.potion.Locator.update;

public record CreateTaskQueues(TaskRouter router) implements Task {
  @Override
  public void exec() {
    try {
      forEach(SkillQueue.disconnected, q -> {
        if(q.getSid() == null) {
          var w = router.createTaskQueue(q.getName(), q.getQueueExpression());
          update(q, "CreateTaskQueues", copy -> {
            copy.setSid(w.getSid());
            log.info("CreateTaskQueues: %s -> %s", q.getName(), w.getSid());
          });
        }
        if(q.getWorkflowSid() == null) {
          var flow = router.createWorkFlow(q.getName(),q.getSid());
          update(q, "CreateTaskQueues", copy -> {
            copy.setWorkflowSid(flow.getSid());
            log.info("CreateTaskQueues workflow: %s -> %s", q.getName(),flow.getSid());
          });
        }

      });
    } catch(Throwable t) {
      log.error(t);
    }
  }
  private static final Log log = Log.getInstance(CreateTaskQueues.class);
}
