package com.ameriglide.phenix.util;

import com.ameriglide.phenix.common.Call;
import com.ameriglide.phenix.common.Lead;
import com.ameriglide.phenix.servlet.Startup;
import net.inetalliance.cli.Cli;
import net.inetalliance.potion.Locator;
import net.inetalliance.sql.OrderBy;
import net.inetalliance.util.ProgressMeter;

import java.util.concurrent.atomic.AtomicInteger;

public class RepairLeadSource implements Runnable {
  public static void main(String[] args) {
    Startup.bootstrap();
    try {
      Cli.run(new RepairLeadSource(), args);
    } finally {
      Startup.teardown();
    }
  }

  @Override
  public void run() {
    var q = Lead.withSource(null);
    var n = Locator.count(q);
    var meter = new ProgressMeter(n);
    var numRepaired = new AtomicInteger();
    var numProcessed = new AtomicInteger();
    var numMissingSource = new AtomicInteger();
    Locator.forEach(q, l -> {
      numProcessed.incrementAndGet();
      var firstCall = Locator.$1(
        Call.isQueue.and(Call.withPhone(l.getContact().getPhone()).orderBy("created", OrderBy.Direction.ASCENDING)));
      if (firstCall!=null) {
        var source = firstCall.getSource();
        if (source==null) {
          numMissingSource.incrementAndGet();
        } else {
          Locator.update(l, "RepairLeadSource", copy -> {
            copy.setSource(source);
          });
          numRepaired.incrementAndGet();
        }
      }
      var r = numRepaired.get();
      var p = numProcessed.get();
      var rate = (100.0f * r) / p;
      meter.increment("fixed %d of %d (%.2f%%), no src: %d, to go: %d", numRepaired.get(),
        numProcessed.get(), rate,
        numMissingSource.get(), n-p);

    });

  }
}
