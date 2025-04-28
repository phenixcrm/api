package net.inetalliance.potion;

import java.net.URI;
import net.inetalliance.sql.Db;
import net.inetalliance.types.annotations.Parameter;
import net.inetalliance.types.annotations.Required;

public abstract class DbCli
    implements Runnable {

  @Parameter('d')
  @Required
  protected URI db;

  @Override
  public void run() {
    try {
      Locator.attach(new Db(db));
      exec();
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  protected abstract void exec()
      throws Throwable;
}
