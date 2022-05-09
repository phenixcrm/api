package com.ameriglide.phenix;

import com.ameriglide.phenix.common.Ticket;
import com.ameriglide.phenix.tasks.CreateWorkers;
import com.ameriglide.phenix.tasks.CredentialWorkers;
import com.ameriglide.phenix.tasks.PruneWorkers;
import com.ameriglide.phenix.util.Security;
import com.twilio.Twilio;
import com.twilio.http.TwilioRestClient;
import com.twilio.rest.api.v2010.account.sip.CredentialList;
import com.twilio.rest.api.v2010.account.sip.CredentialListFetcher;
import com.twilio.rest.api.v2010.account.sip.Domain;
import com.twilio.rest.api.v2010.account.sip.DomainFetcher;
import com.twilio.rest.taskrouter.v1.Workspace;
import com.twilio.rest.taskrouter.v1.WorkspaceFetcher;
import com.twilio.rest.taskrouter.v1.workspace.*;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.stream.StreamSupport.stream;

public class TaskRouter {
  public final Workspace workspace;
  public final TwilioRestClient rest;
  public final Activity offline;
  public final Activity available;
  public final Activity unavailable;
  public final Map<String,Activity> bySid;

  public final Domain sip;

  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
  public final CredentialList sipCredentialList;

  public TaskRouter() {
    var env = Dotenv.load();
    Twilio.init(env.get("twilioAccountSid"),env.get("twilioAuthToken"));
    rest = Twilio.getRestClient();
    sip = new DomainFetcher(rest.getAccountSid(),env.get("twilioDomainSid")).fetch(rest);
    workspace = new WorkspaceFetcher(env.get("twilioWorkspaceSid")).fetch(rest);
    sipCredentialList = new CredentialListFetcher(env.get("twilioCredentialListSid")).fetch(rest);
    offline = findActivity("Offline");
    available = findActivity("Available");
    unavailable = findActivity("Unavailable");
    bySid = new HashMap<>();
    bySid.put(offline.getSid(),offline);
    bySid.put(available.getSid(),available);
    bySid.put(unavailable.getSid(),unavailable);
    executor.scheduleWithFixedDelay(new CreateWorkers(this),0,5, MINUTES);
    executor.scheduleWithFixedDelay(new PruneWorkers(this),0,15, MINUTES);
    executor.scheduleWithFixedDelay(new CredentialWorkers(sipCredentialList.getSid(), new Security(env.get("key"))),
      0,15, MINUTES);
  }
  private Activity findActivity(final String name) {
    return stream(new ActivityReader(workspace.getSid())
      .setFriendlyName(name)
      .read(rest).spliterator(), false
    ).findFirst().orElseThrow();
  }

  public Stream<Activity> getActivities() {
    return stream(new ActivityReader(workspace.getSid()).read(rest).spliterator(),false);
  }

  public Stream<Worker> getWorkers() {
    return stream(new WorkerReader(workspace.getSid()).read(rest).spliterator(),true);
  }

  public void shutdown() {
    executor.shutdown();
  }

  public Worker setActivity(Ticket ticket, Activity newActivity) {
    return new WorkerUpdater(workspace.getSid(),ticket.sid()).setActivitySid(newActivity.getSid()).update();
  }
}
