package com.ameriglide.phenix;

import com.ameriglide.phenix.common.Agent;
import com.ameriglide.phenix.common.Ticket;
import com.ameriglide.phenix.common.VerifiedCallerId;
import com.ameriglide.phenix.tasks.*;
import com.ameriglide.phenix.util.Security;
import com.twilio.Twilio;
import com.twilio.http.TwilioRestClient;
import com.twilio.rest.api.v2010.account.IncomingPhoneNumberReader;
import com.twilio.rest.api.v2010.account.OutgoingCallerIdReader;
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
import io.github.cdimascio.dotenv.Dotenv;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.stream.StreamSupport.stream;

public class TaskRouter {
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

  public final Map<String,Boolean> byAgent;

  public TaskRouter() {
    var env = Dotenv.load();
    Twilio.init(env.get("twilioAccountSid"), env.get("twilioAuthToken"));
    rest = Twilio.getRestClient();
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
    executor.scheduleWithFixedDelay(new SyncWorkerStatus(this),0,1,MINUTES);
    executor.scheduleWithFixedDelay(new CreateWorkers(this), 0, 5, MINUTES);
    executor.scheduleWithFixedDelay(new PruneWorkers(this), 0, 15, MINUTES);
    this.security = new Security(env.get("key"));
    executor.scheduleWithFixedDelay(new CredentialWorkers(sipCredentialList.getSid(),
      security), 0, 15, MINUTES);
    executor.scheduleWithFixedDelay(new PruneCredentials(this), 0, 15, MINUTES);
    executor.scheduleWithFixedDelay(new CreateVerifiedCallerIds(this),0, 5,MINUTES);
    executor.scheduleWithFixedDelay(new PruneVerifiedCallerIds(this),2, 5,MINUTES);
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

  public Stream<VerifiedCallerId> getVerifiedCallerIds() {
    return Stream.concat(
      stream(new OutgoingCallerIdReader().read(rest).spliterator(),false)
        .map(oCid -> new VerifiedCallerId(oCid.getSid(),oCid.getFriendlyName(),oCid.getPhoneNumber().getEndpoint())),
      stream(new IncomingPhoneNumberReader().read(rest).spliterator(),false)
        .map(iPn -> new VerifiedCallerId(iPn.getSid(),iPn.getFriendlyName(),iPn.getPhoneNumber().getEndpoint())));
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
}
