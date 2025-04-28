package net.inetalliance.potion.info;

public class PersistenceError
    extends Error {

  public PersistenceError(final String message, final Object... parameters) {
    this(String.format(message, parameters));
  }

  public PersistenceError(final String message) {
    super(message);
  }

  public PersistenceError(final Throwable t) {
    super(t);
  }
}
