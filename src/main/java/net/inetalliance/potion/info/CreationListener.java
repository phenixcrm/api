package net.inetalliance.potion.info;

public interface CreationListener {

  void onCreate();

  default void afterCreation() {

  }
}
