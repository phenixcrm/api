package com.ameriglide.phenix.util;

import com.ameriglide.phenix.Startup;
import com.ameriglide.phenix.common.Call;
import com.ameriglide.phenix.common.Contact;
import com.ameriglide.phenix.common.Lead;
import com.ameriglide.phenix.common.VerifiedCallerId;
import com.ameriglide.phenix.core.DateTimeFormats;
import com.ameriglide.phenix.core.Log;
import com.ameriglide.phenix.twilio.TaskRouter;
import net.inetalliance.cli.Cli;
import net.inetalliance.potion.Locator;
import net.inetalliance.util.ProgressMeter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static com.ameriglide.phenix.common.Source.PHONE;
import static com.ameriglide.phenix.common.Source.PRINT;

public class RepairMediaBids implements Runnable {
  private final static Log log = new Log();

  public static void main(String[] args) {
    Cli.run(new RepairMediaBids(), args);
  }

  @Override
  public void run() {

    try {
      Startup.bootstrap();
      var restricted = new AtomicInteger(0);
      var total = new AtomicInteger(0);
      var missing = new AtomicInteger(0);
      var updated = new AtomicInteger(0);
      var mediabidsDid = Locator.$1(VerifiedCallerId.withSource(PRINT));
      var dateParser = DateTimeFormats.ofPattern("yyyy-MM-dd HH:mm:ss");
      try (Stream<String> stream = Files.lines(Path.of("mediabids.csv"), StandardCharsets.UTF_8)) {
        var n = (int)stream.count();
        var meter = new ProgressMeter(n);
        ImportCSV.processCsv("mediabids.csv", r -> {
          total.incrementAndGet();
          var cid = r.get("Caller ID");
          switch (cid) {
            case "Restricted":
              restricted.incrementAndGet();
            default:
              var e164 = TaskRouter.toE164(cid);
              var contact = Locator.$1(Contact.withPhoneNumber(e164));
              if (contact==null) {
                missing.incrementAndGet();
              } else {
                var time = dateParser.parse(r.get("Call Start Time"),LocalDateTime::from);
                Locator.forEach(Lead.withContact(contact), o -> {
                  if (o.getCreated().isAfter(time)) {
                    var firstCall = Locator.$1(Call.withPhone(contact.getPhone()).orderBy("created"));
                    if (firstCall!=null) {
                      var did = firstCall.getDialedNumber();
                      if (did==null) {
                        Locator.update(firstCall, "RepairMediaBids", copy -> {
                          copy.setSource(PRINT);
                          copy.setDialedNumber(mediabidsDid);
                        });
                      }
                      var source = o.getSource();
                      if (source==null || source==PHONE) {
                        Locator.update(o, "RepairMediaBids", copy -> {
                          copy.setSource(PRINT);
                        });
                        updated.incrementAndGet();
                      }
                      ;
                    }

                  }
                });
              }
          }
          meter.increment("n: %d, r: %d, m: %d, u: %d".formatted(total.get(), restricted.get(),missing.get(),updated.get()));
        });
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    } finally {
      Startup.teardown();
    }
  }
}
