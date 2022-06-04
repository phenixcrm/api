package com.ameriglide.phenix;

import com.ameriglide.phenix.common.Agent;
import com.ameriglide.phenix.common.Ticket;
import com.ameriglide.phenix.common.VerifiedCallerId;
import com.ameriglide.phenix.tasks.*;
import com.ameriglide.phenix.util.Security;
import com.twilio.Twilio;
import com.twilio.http.HttpMethod;
import com.twilio.http.TwilioRestClient;
import com.twilio.rest.api.v2010.account.*;
import com.twilio.rest.api.v2010.account.sip.CredentialList;
import com.twilio.rest.api.v2010.account.sip.CredentialListFetcher;
import com.twilio.rest.api.v2010.account.sip.Domain;
import com.twilio.rest.api.v2010.account.sip.DomainFetcher;
import com.twilio.rest.api.v2010.account.sip.credentiallist.Credential;
import com.twilio.rest.api.v2010.account.sip.credentiallist.CredentialDeleter;
import com.twilio.rest.api.v2010.account.sip.credentiallist.CredentialReader;
import com.twilio.rest.taskrouter.v1.Workspace;
import com.twilio.rest.taskrouter.v1.WorkspaceFetcher;
import com.twilio.rest.taskrouter.v1.workspace.*;
import com.twilio.type.Endpoint;
import com.twilio.type.PhoneNumber;
import io.github.cdimascio.dotenv.Dotenv;
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

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.stream.StreamSupport.stream;
import static net.inetalliance.potion.Locator.$1;
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
    executor.scheduleWithFixedDelay(new CredentialWorkers(sipCredentialList.getSid(),
      security), 0, 15, MINUTES);
    executor.scheduleWithFixedDelay(new PruneCredentials(this), 0, 15, MINUTES);
    executor.scheduleWithFixedDelay(new CreateVerifiedCallerIds(this), 0, 5, MINUTES);
    executor.scheduleWithFixedDelay(new PruneVerifiedCallerIds(this), 2, 5, MINUTES);
    executor.scheduleWithFixedDelay(new CreateTaskQueues(this), 0, 30, MINUTES);
  }
  private Stream<TaskQueue> getTaskQueues() {
    return stream(new TaskQueueReader(workspace.getSid()).read(rest).spliterator(),false);
  }
  public TaskQueue createTaskQueue(String name, String workerExpression) {
    return TaskQueue.creator(workspace.getSid(),name)
      .setTaskOrder(TaskQueue.TaskOrder.FIFO)
      .setTargetWorkers(workerExpression)
      .create(rest);

  }

  private Activity findActivity(final String name) {
    return stream(new ActivityReader(workspace.getSid())
      .setFriendlyName(name)
      .read(rest).spliterator(), false
    ).findFirst().orElseThrow();
  }

  public Stream<Activity> getActivities() {
    return stream(new ActivityReader(workspace.getSid()).read(rest).spliterator(), false);
  }

  public Stream<Worker> getWorkers() {
    return stream(new WorkerReader(workspace.getSid()).read(rest).spliterator(), false);
  }

  public Stream<Credential> getCredentials() {
    return stream(new CredentialReader(sipCredentialList.getSid()).read(rest).spliterator(), false);
  }

  private URI getAbsolutePath(String relativePath, String qs) {
    var base = app.getVoiceUrl();
    try {
      return new URI(base.getScheme(), base.getHost(), relativePath, qs, null);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException();
    }
  }

  public Call call(final Endpoint to, String url, String qs) {
    var vCid = $1(VerifiedCallerId.isDefault);
    return new CallCreator(to, new PhoneNumber(vCid.getPhoneNumber()), getAbsolutePath(url, qs))
      .setStatusCallbackMethod(HttpMethod.GET)
      .setStatusCallbackEvent(List.of("answered", "completed"))
      .setStatusCallback("/api/twilio/voice/status")
      .create(rest);
  }

  public Stream<VerifiedCallerId> getVerifiedCallerIds() {
    return Stream.concat(
      stream(new OutgoingCallerIdReader().read(rest).spliterator(), false)
        .map(oCid -> new VerifiedCallerId(oCid.getSid(), oCid.getFriendlyName(), oCid.getPhoneNumber().getEndpoint())),
      stream(new IncomingPhoneNumberReader().read(rest).spliterator(), false)
        .map(iPn -> new VerifiedCallerId(iPn.getSid(), iPn.getFriendlyName(), iPn.getPhoneNumber().getEndpoint())));
  }

  public void shutdown() {
    executor.shutdown();
  }

  public Worker setActivity(Ticket ticket, Activity newActivity) {
    return new WorkerUpdater(workspace.getSid(), ticket.sid()).setActivitySid(newActivity.getSid()).update(rest);
  }

  public boolean delete(Worker w) {
    return new WorkerDeleter(workspace.getSid(), w.getSid()).delete(rest);
  }

  public boolean deleteCredential(String credentialSid) {
    return new CredentialDeleter(sipCredentialList.getSid(), credentialSid).delete(rest);
  }

  public Worker createWorker(Agent a) {
    return Worker.creator(workspace.getSid(), a.getFullName()).create(rest);
  }

  public String getSipSecret(Agent a) {
    var bytes = a.getSipSecret();
    return bytes == null ? null : security.decrypt(bytes);
  }

  public void updateSkills(Agent a) {
    new WorkerUpdater(workspace.getSid(),a.getTwilioSid()).setAttributes(a.getSkills()).update();
  }

  public Workflow createWorkFlow(String name, String queueSid) {
    return new WorkflowCreator(workspace.getSid(),name,
      ugly(new JsonMap()
        .$("task_routing", new JsonMap()
          .$("default_filter", new JsonMap()
            .$("queue",queueSid)))))
      .create(rest);
  }
}
