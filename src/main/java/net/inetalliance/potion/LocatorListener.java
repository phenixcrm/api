package net.inetalliance.potion;

public interface LocatorListener {

  void create(final Object object);

  void update(final Object old, final Object updated);

  void delete(final Object object);
}
