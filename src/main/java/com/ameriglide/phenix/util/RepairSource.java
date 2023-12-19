package com.ameriglide.phenix.util;

import com.ameriglide.phenix.Startup;
import com.ameriglide.phenix.common.Call;
import com.ameriglide.phenix.common.VerifiedCallerId;
import com.ameriglide.phenix.core.Log;
import com.ameriglide.phenix.core.Strings;
import net.inetalliance.cli.Cli;
import net.inetalliance.potion.Locator;

import static net.inetalliance.sql.OrderBy.Direction.DESCENDING;

public class RepairSource implements Runnable{
  private static final Log log = new Log();

  public static void main(String[] args) {
    Cli.run(new RepairSource(), args);
  }

  public void run() {
    Startup.bootstrap();
    try {
      var q = Call.isQueue.and(Call.withoutDialedNumber().orderBy("created", DESCENDING));
      int n = Locator.count(q);
      var meter = new ProgressMeter(n);
      Locator.forEach(q, c -> {
        var call = Startup.router.getCall(c.sid);
        var dialedNumber = call.getPhoneNumberSid();
        if (Strings.isNotEmpty(dialedNumber)) {
          var did = Locator.$(new VerifiedCallerId(dialedNumber));
          if (did==null) {
            meter.increment("No match for %s",dialedNumber);
          } else {
            Locator.update(c, "RepairSource", copy -> {
              copy.setDialedNumber(did);
              copy.setSource(did.getSource());
            });
            meter.increment(
               "dialed number %s [%s] assigned for %s",did.getPhoneNumber(), did.getSource(), c.sid);
          }
        }
      });
    } finally {
      Startup.teardown();
    }

  }
}
