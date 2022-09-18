package com.ameriglide.phenix.ws;

import com.ameriglide.phenix.api.Hud;
import com.ameriglide.phenix.core.ExecutorServices;
import com.ameriglide.phenix.core.Log;
import jakarta.websocket.Session;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.concurrent.TimeUnit.SECONDS;

public class HudHandler implements JsonMessageHandler {

    private static final Log log = new Log();
    private static final ExecutorService service = Executors.newFixedThreadPool(4, (r) -> {
        var t = new Thread(r);
        t.setDaemon(true);
        return t;
    });
    private final Set<Session> subscribers;

    HudHandler() {
        subscribers = Collections.synchronizedSet(new HashSet<>(8));

    }

    public static void shutdown() {
        ExecutorServices.shutdown("HUD Event Handler", service);
    }

    @Override
    public JsonMap onMessage(final Session session, final JsonMap msg) {
        try {
            final Action action = Action.valueOf(msg.get("action").toUpperCase());
            switch (action) {
                case SUBSCRIBE -> {
                    subscribers.add(session);
                    log.debug(() -> "Added HUD subscription for %s, #:%d".formatted(
                            Events.getTicket(session).agent().getSipUser(), subscribers.size()));
                    return Hud.map;
                }
                case UNSUBSCRIBE -> {
                    subscribers.remove(session);
                    log.debug(() -> "Removed HUD subscription for %s, #:%d".formatted(
                            Events.getTicket(session).agent().getSipUser(), subscribers.size()));
                }
            }
        } catch (IllegalArgumentException e) {
            log.error(e);
        }
        return null;
    }

    public void changed(final JsonMap map) {
        broadcast(map);
    }

    private void broadcast(final JsonMap msg) {
        final var latch = new CountDownLatch(subscribers.size());
        final var toRemove = new ArrayList<Session>(0);
        try {
            for (final var subscriber : subscribers) {
                service.submit(() -> {
                    try {
                        subscriber.getBasicRemote().sendText(Json.ugly(msg));
                    } catch (IOException e) {
                        toRemove.add(subscriber);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            if (!latch.await(1, SECONDS)) {
                log.warn(() -> "slow status broadcast");
            }
        } catch (InterruptedException e) {
            // oh well, we'll get 'em next time
        } finally {
            // remove any dead ones we found
            toRemove.forEach(subscribers::remove);
        }
    }

    enum Action {
        SUBSCRIBE, UNSUBSCRIBE
    }

}
