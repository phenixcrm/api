package net.inetalliance.potion.obj;

import net.inetalliance.potion.info.Info;
import net.inetalliance.potion.query.Query;

/**
 * A flag interface which triggers a self-referential foreign key in the classloader agent
 */
public interface Prototyped {

  final class Q {

    public static <O extends Prototyped> Query<O> replicas(final O prototype) {
      return Query.eq(Info.$(prototype).type, "prototype", prototype);
    }
  }
}
