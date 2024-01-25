package com.ameriglide.phenix.api;

import com.ameriglide.phenix.core.Log;
import net.inetalliance.util.ProgressMeter;
import com.ameriglide.phenix.ws.Events;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;
import net.inetalliance.types.www.ContentType;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Writer;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class ProgressHandler {

  private static final OutputStream nullStream = new OutputStream() {
    @Override
    public void write(final int b) {

    }
  };
  public static ProgressHandler $ = new ProgressHandler();
  private static final Log log = new Log();
  private final Executor exec = Executors.newCachedThreadPool(runnable -> {
    var t = new Thread(runnable);
    t.setDaemon(true);
    return t;
  });
  private final AtomicInteger id = new AtomicInteger();

  private ProgressHandler() {
    super();
    $ = this;
  }

  public void start(final Integer agent, final HttpServletResponse response, final int max,
                    final Function<ProgressMeter, Json> proc) throws IOException {
    final int jobId = id.getAndIncrement();
    try (Writer writer = response.getWriter()) {
      response.setContentType(ContentType.JSON.toString());
      writer.write(String.format("{\"job\":%d, \"max\":%d}", jobId, max));
      writer.flush();
    }
    exec.execute(new Runnable() {
      @Override
      public void run() {
        try {
          final Json result = proc.apply(new ProgressMeter(new PrintStream(nullStream), max) {
            @Override
            public void increment(final String label, final Object... args) {
              super.increment(label, args);
              setLabel(String.format(label, args));
            }

            protected void onIncrement(final int delta, final String label) {
              Events.broadcast("progress", agent,
                new JsonMap().$("position", this.position).$("label", label==null ? "":label).$("job", jobId));
            }

            @Override
            public void setLabel(final String label) {
              super.setLabel(label);
              Events.broadcast("progress", agent, new JsonMap().$("label", label).$("job", jobId));
            }
          });
          Events.broadcast("progress", agent, new JsonMap().$("result", result).$("job", jobId));
        } catch (Throwable t) {
          log.error(t);
          Events.broadcast("progress", agent, new JsonMap().$("error", t.getMessage()).$("job", jobId));
        }
      }
    });
  }
}
