package com.ameriglide.phenix;

import com.ameriglide.phenix.common.Agent;
import com.ameriglide.phenix.common.Ticket;
import com.ameriglide.phenix.common.VerifiedCallerId;
import com.ameriglide.phenix.tasks.*;
import com.ameriglide.phenix.util.Security;
import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.http.TwilioRestClient;
import com.twilio.rest.api.v2010.account.*;
import com.twilio.rest.api.v2010.account.sip.CredentialList;
import com.twilio.rest.api.v2010.account.sip.CredentialListFetcher;
import com.twilio.rest.api.v2010.account.sip.Domain;
import com.twilio.rest.api.v2010.account.sip.DomainFetcher;
import com.twilio.rest.api.v2010.account.sip.credentiallist.Credential;
import com.twilio.rest.taskrouter.v1.Workspace;
import com.twilio.rest.taskrouter.v1.WorkspaceFetcher;
import com.twilio.rest.taskrouter.v1.workspace.Activity;
import com.twilio.rest.taskrouter.v1.workspace.TaskQueue;
import com.twilio.rest.taskrouter.v1.workspace.Worker;
import com.twilio.rest.taskrouter.v1.workspace.Workflow;
import com.twilio.rest.taskrouter.v1.workspace.task.Reservation;
import com.twilio.type.PhoneNumber;
import com.twilio.type.Sip;
import io.github.cdimascio.dotenv.Dotenv;
import net.inetalliance.log.Log;
import net.inetalliance.potion.Locator;
import net.inetalliance.types.json.JsonMap;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.twilio.http.HttpMethod.GET;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.stream.StreamSupport.stream;
import static net.inetalliance.potion.Locator.$1;
import static net.inetalliance.potion.Locator.update;
import static net.inetalliance.types.json.Json.ugly;

public class TaskRouter {
  private final Application app;
  private final Workspace workspace;
  private final TwilioRestClient rest;
  public final Activity offline;
  public final Activity available;
  public final Activity unavailable;
  public final Domain domain;
  public final Map<String, Activity> bySid;

  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
  private final CredentialList sipCredentialList;
  private final Security security;

  public final Map<String, Boolean> byAgent;
  private final CredentialWorkers credentialTask;

  public TaskRouter() {
    var env = Dotenv.load();
    Twilio.init(env.get("twilioAccountSid"), env.get("twilioAuthToken"));
    rest = Twilio.getRestClient();
    app = StreamSupport.stream(new ApplicationReader().setFriendlyName("Phenix").read(rest).spliterator(), false)
      .findFirst()
      .orElseThrow(IllegalStateException::new);
    domain = new DomainFetcher(env.get("twilioDomainSid")).fetch(rest);
    workspace = new WorkspaceFetcher(env.get("twilioWorkspaceSid")).fetch(rest);
    sipCredentialList = new CredentialListFetcher(env.get("twilioCredentialListSid")).fetch(rest);
    offline = findActivity("Offline");
    available = findActivity("Available");
    unavailable = findActivity("Unavailable");
    bySid = new HashMap<>();
    bySid.put(offline.getSid(), offline);
    bySid.put(available.getSid(), available);
    bySid.put(unavailable.getSid(), unavailable);
    byAgent = Collections.synchronizedMap(new HashMap<>());
    executor.scheduleWithFixedDelay(new SyncWorkerStatus(this), 0, 1, MINUTES);
    executor.scheduleWithFixedDelay(new CreateWorkers(this), 0, 5, MINUTES);
    executor.scheduleWithFixedDelay(new PruneWorkers(this), 0, 15, MINUTES);
    this.security = new Security(env.get("key"));
    credentialTask = new CredentialWorkers(this, security);
    executor.scheduleWithFixedDelay(credentialTask, 0, 15, MINUTES);
    executor.scheduleWithFixedDelay(new PruneCredentials(this), 0, 15, MINUTES);
    executor.scheduleWithFixedDelay(new CreateVerifiedCallerIds(this), 0, 5, MINUTES);
    executor.scheduleWithFixedDelay(new PruneVerifiedCallerIds(this), 2, 5, MINUTES);
    executor.scheduleWithFixedDelay(new CreateTaskQueues(this), 0, 30, MINUTES);
  }
  private Stream<TaskQueue> getTaskQueues() {
    return stream(TaskQueue.reader(workspace.getSid()).read(rest).spliterator(),false);
  }
  public TaskQueue createTaskQueue(String name, String workerExpression) {
    return TaskQueue.creator(workspace.getSid(),name)
      .setTaskOrder(TaskQueue.TaskOrder.FIFO)
      .setTargetWorkers(workerExpression)
      .create(rest);

  }

  private Activity findActivity(final String name) {
    return stream(Activity.reader(workspace.getSid())
      .setFriendlyName(name)
      .read(rest).spliterator(), false
    ).findFirst().orElseThrow();
  }

  public Stream<Activity> getActivities() {
    return stream(Activity.reader(workspace.getSid()).read(rest).spliterator(), false);
  }

  public Stream<Worker> getWorkers() {
    return stream(Worker.reader(workspace.getSid()).read(rest).spliterator(), false);
  }

  public Stream<Credential> getCredentials() {
    return stream(Credential.reader(sipCredentialList.getSid()).read(rest).spliterator(), false);
  }

  public URI getAbsolutePath(String relativePath, String qs) {
    var base = app.getVoiceUrl();
    try {
      return new URI(base.getScheme(), base.getHost(), relativePath, qs, null);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException();
    }
  }
  public Call call(final Sip from, Sip to, String url, String qs) {
    return Call.creator(to,from,getAbsolutePath(url,qs))
      .setStatusCallbackMethod(GET)
      .setStatusCallbackEvent(List.of("answered", "completed"))
      .setStatusCallback("/api/twilio/voice/status")
      .create(rest);

  }

  public Call call(final Sip to, String url, String qs) {
    var vCid = $1(VerifiedCallerId.isDefault);
    return Call.creator(to, new PhoneNumber(vCid.getPhoneNumber()), getAbsolutePath(url, qs))
      .setStatusCallbackMethod(GET)
      .setStatusCallbackEvent(List.of("answered", "completed"))
      .setStatusCallback("/api/twilio/voice/status")
      .create(rest);
  }

  public Stream<VerifiedCallerId> getVerifiedCallerIds() {
    return Stream.concat(
      stream(OutgoingCallerId.reader().read(rest).spliterator(), false)
        .map(oCid -> new VerifiedCallerId(oCid.getSid(), oCid.getFriendlyName(), oCid.getPhoneNumber().getEndpoint())),
      stream(IncomingPhoneNumber.reader().read(rest).spliterator(), false)
        .map(iPn -> new VerifiedCallerId(iPn.getSid(), iPn.getFriendlyName(), iPn.getPhoneNumber().getEndpoint())));
  }

  public void shutdown() {
    executor.shutdown();
  }

  public Worker setActivity(Ticket ticket, Activity newActivity) {
    return Worker.updater(workspace.getSid(), ticket.sid()).setActivitySid(newActivity.getSid()).update(rest);
  }

  public boolean delete(Worker w) {
    return Worker.deleter(workspace.getSid(), w.getSid()).delete(rest);
  }

  public boolean deleteCredential(String credentialSid) {
    return Credential.deleter(sipCredentialList.getSid(), credentialSid).delete(rest);
  }

  public Worker createWorker(Agent a) {
    try {
      return Worker.creator(workspace.getSid(), a.getFullName()).create(rest);
    } catch (ApiException e) {
      if(e.getCode() == 20001) { // worker with name already exists
        var w = stream(Worker.reader(workspace.getSid()).setFriendlyName(a.getFullName()).read(rest).spliterator(),
          false)
          .findFirst()
          .orElseThrow(()->e);
        update(a,"TaskRouter", copy -> {
          copy.setSid(w.getSid());
        });
        log.info("assigned existing worker %s -> %s", w.getSid(), a.getFullName());
        return w;
      }
      throw e;
    }
  }

  public String getSipSecret(Agent a) {
    var bytes = a.getSipSecret();
    return bytes == null ? null : security.decrypt(bytes);
  }

  public void updateSkills(Agent a) {
    try {
      Worker.updater(workspace.getSid(), a.getSid())
        .setAttributes(a.getSkills())
        .update(rest);
    } catch(Throwable t) {
      log.error("UPDATE SKILLS ERROR", t);
    }
  }
  private static final Log log = Log.getInstance(TaskRouter.class);

  public Workflow createWorkFlow(String name, String queueSid) {
    return Workflow.creator(workspace.getSid(),name,
      ugly(new JsonMap()
        .$("task_routing", new JsonMap()
          .$("default_filter", new JsonMap()
            .$("queue",queueSid)))))
      .setAssignmentCallbackUrl(getAbsolutePath("/twilio/assignment",null))
      .create(rest);
  }

  public Reservation getReservation(String taskSid, String reservationSid) {
    return Reservation.fetcher(workspace.getSid(),taskSid, reservationSid).fetch(rest);
  }


  public synchronized Worker getWorker(String sid) {
    try {
      return Worker.fetcher(workspace.getSid(), sid).fetch(rest);
    } catch(ApiException e) {
      if(e.getCode() == 20404) {
        var a = Locator.$1(Agent.withSid(sid));
          if(a != null) {
            return createWorker(a);
          }
      }
      throw e;
    }
  }

  public void credential(Agent a) {
    credentialTask.credential(a);
  }

  public Credential createCredentials(String sipUser, String secret) {
    return Credential.creator(sipCredentialList.getSid(), sipUser, secret).create(rest);
  }
}
