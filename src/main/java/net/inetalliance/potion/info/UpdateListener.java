package net.inetalliance.potion.info;

public interface UpdateListener<O> {

  void onUpdate(final O old);

  default void afterUpdate() {

  }
}
