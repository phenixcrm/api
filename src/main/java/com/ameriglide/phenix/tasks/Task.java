package com.ameriglide.phenix.tasks;

import net.inetalliance.log.Log;

public interface Task extends Runnable{

  void exec();

  default void run() {
    try {
      exec();
    }catch (Throwable t) {
      log.error(t);
    }

  }
  Log log = Log.getInstance(Task.class);
}
