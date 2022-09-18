package com.ameriglide.phenix.model;

import com.ameriglide.phenix.core.Log;
import com.ameriglide.phenix.servlet.PhenixServlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.types.json.Json;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public abstract class JsonCronServlet extends PhenixServlet implements Runnable {

    public static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, (r) -> {
        var t = new Thread(r);
        t.setDaemon(true);
        return t;
    });
    private static final Log log = new Log();
    private final int interval;
    private final TimeUnit timeUnit;
    private transient String content;

    public JsonCronServlet(final int interval, final TimeUnit timeUnit) {
        this.interval = interval;
        this.timeUnit = timeUnit;
    }

    @Override
    protected void get(final HttpServletRequest request, final HttpServletResponse response) throws Exception {
        response.setHeader("Expires", "-1");
        response.setContentType("application/json");
        try (var writer = response.getWriter()) {
            if (content==null) {
                run();
            }
            writer.print(content);
            writer.flush();
        }
    }

    @Override
    public synchronized void run() {
        String oldContent = content;
        content = Json.pretty(produce());
        afterProduce(oldContent, content);
    }

    protected abstract Json produce();

    protected void afterProduce(String before, String after) {
        log.trace(() -> "After Produce %s->%s".formatted(before, after));
    }

    @Override
    public void destroy() {
        scheduler.shutdown();
    }

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);
        scheduler.scheduleWithFixedDelay(this, 0, interval, timeUnit);
    }
}
