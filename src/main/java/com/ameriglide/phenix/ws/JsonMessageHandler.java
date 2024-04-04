package com.ameriglide.phenix.ws;

import com.ameriglide.phenix.core.Log;
import jakarta.websocket.Session;
import net.inetalliance.types.json.JsonMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.concurrent.TimeUnit.SECONDS;

public interface JsonMessageHandler {

  JsonMap onMessage(final Session session, final JsonMap msg);

  default void onAsyncMessage(List<Session> sessions, final JsonMap msg) {
  }

  default JsonMap onConnect(final Session session) {
    return null;
  }

  default void destroy() {
  }

  ExecutorService service = Executors.newFixedThreadPool(4, (r) -> {
    var t = new Thread(r);
    t.setDaemon(true);
    return t;
  });
  Log log = new Log();
  default void broadcast(Collection<Session> subscribers, final String msg) {
    final var latch = new CountDownLatch(subscribers.size());
    final var toRemove = new ArrayList<Session>(0);
    try {
      for (final var subscriber : subscribers) {
        service.submit(() -> {
          try {
            subscriber.getBasicRemote().sendText(msg);
          } catch (IOException e) {
            toRemove.add(subscriber);
          } finally {
            latch.countDown();
          }
        });
      }
      if (!latch.await(1, SECONDS)) {
        log.warn(() -> "slow broadcast");
      }
    } catch (InterruptedException e) {
      // oh well, we'll get 'em next time
    } finally {
      // remove any dead ones we found
      toRemove.forEach(subscribers::remove);
    }
  }

}
