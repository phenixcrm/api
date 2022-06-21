package com.ameriglide.phenix.tasks;

import com.ameriglide.phenix.TaskRouter;
import com.ameriglide.phenix.common.SkillQueue;
import net.inetalliance.log.Log;

import static net.inetalliance.funky.StringFun.isNotEmpty;
import static net.inetalliance.potion.Locator.forEach;
import static net.inetalliance.potion.Locator.update;

public record SyncQueues(TaskRouter router) implements Task {
  @Override
  public void exec() {
    try {
      forEach(SkillQueue.connected, q-> {
        if(isNotEmpty(q.getWorkflowSid())) {
          var w = router.getWorkflow(q.getWorkflowSid());
          if(w == null) {
            log.info("SyncQueues clearing missing workspace sid %s -%s", q.getName(), q.getWorkflowSid());
            update(q, "SyncQueues", copy -> {
              copy.setWorkflowSid(null);
            });
          }
        }
        if(isNotEmpty(q.getSid())) {
          var tq = router.getTaskQueue(q.getSid());
          if (tq == null) {
            log.info("SyncQueues clearing missing queue sid %s -%s", q.getName(), q.getSid());
            update(q, "SyncQueues", copy -> {
              copy.setSid(null);
            });
          } else if(!q.getQueueExpression().equals(tq.getTargetWorkers())) {
            router.updateQueueExpression(tq,q.getQueueExpression());
            log.info("SyncQueues updating queue expression for %s to %s", q.getName(),q.getQueueExpression());
          }
        }

        });
      forEach(SkillQueue.disconnected, q -> {
        if(q.getSid() == null) {
          var w = router.createTaskQueue(q.getName(), q.getQueueExpression());
          update(q, "CreateTaskQueues", copy -> {
            copy.setSid(w.getSid());
            log.info("SyncQueues: %s -> %s", q.getName(), w.getSid());
          });
        }
        if(q.getWorkflowSid() == null) {
          var flow = router.createWorkFlow(q.getName(),q.getSid());
          update(q, "SyncQueues", copy -> {
            copy.setWorkflowSid(flow.getSid());
            log.info("SyncQueues workflow: %s -> %s", q.getName(),flow.getSid());
          });
        }

      });
    } catch(Throwable t) {
      log.error(t);
    }
  }
  private static final Log log = Log.getInstance(SyncQueues.class);
}
