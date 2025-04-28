package net.inetalliance.potion;

import java.util.concurrent.ThreadFactory;

public class DaemonThreadFactory
    implements ThreadFactory {

  public static final DaemonThreadFactory $ = new DaemonThreadFactory();

  private DaemonThreadFactory() {

  }

  @Override
  public Thread newThread(final Runnable r) {
    final Thread thread = new Thread(r);
    thread.setDaemon(true);
    return thread;
  }
}
