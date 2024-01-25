package com.ameriglide.phenix.util;

import com.ameriglide.phenix.Startup;
import com.ameriglide.phenix.common.Call;
import com.ameriglide.phenix.common.Opportunity;
import com.ameriglide.phenix.common.VerifiedCallerId;
import com.ameriglide.phenix.core.Log;
import com.ameriglide.phenix.core.Strings;
import com.twilio.exception.ApiException;
import net.inetalliance.cli.Cli;
import net.inetalliance.potion.Locator;
import net.inetalliance.sql.DateTimeInterval;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

import static net.inetalliance.sql.OrderBy.Direction.DESCENDING;

public class RepairSource implements Runnable {
  private static final Log log = new Log();

  public static void main(String[] args) {
    Cli.run(new RepairSource(), args);
  }

  public void run() {
    Startup.bootstrap();
    try {
      var q = Call.isQueue.and(Call.withoutDialedNumber().and(Call.isAfter(LocalDate.of(2023,11,19))).orderBy("created", DESCENDING));
      log.info(() -> "Setting missing dialed numbers...");
      Locator.forEachWithProgress(q, (c, meter) -> {
        try {
          var call = Startup.router.getCall(c.sid);
          var dialedNumber = call.getPhoneNumberSid();
          if (Strings.isNotEmpty(dialedNumber)) {
            var did = Locator.$(new VerifiedCallerId(dialedNumber));
            if (did==null) {
              meter.increment("No match for %s", dialedNumber);
            } else {
              Locator.update(c, "RepairSource", copy -> {
                copy.setDialedNumber(did);
                copy.setSource(did.getSource());
              });
              meter.increment("dialed number %s [%s] assigned for %s", did.getPhoneNumber(), did.getSource(), c.sid);
            }
          }
        } catch (ApiException e) {
          meter.increment("could not find call %s at twilio",c.sid);
        }
      });
      log.info(() -> "Changing source by first touch");

      Locator.forEachWithProgress(Opportunity.isSold.and(Opportunity.createdInInterval(new DateTimeInterval(LocalDate.of(2023,11,1).atStartOfDay(),
        LocalDateTime.now()))), (o, meter) -> {
        var firstCall = Locator.$1(Call.withContact(o.getContact()).orderBy("created"));
        if (firstCall!=null) {
          var did = firstCall.getDialedNumber();
          if (did!=null) {
            var callSource = did.getSource();
            if (!Objects.equals(callSource, o.getSource())) {
              System.err.printf("%d, %s, %s%n", o.id, o.getSource(), callSource);
              Locator.update(o, "RepairSource", copy -> {
                copy.setSource(did.getSource());
              });
            }
          }
        }
        meter.increment();
      });
    } finally {
      Startup.teardown();
    }

  }
}
