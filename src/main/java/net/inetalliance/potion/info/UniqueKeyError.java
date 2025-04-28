package net.inetalliance.potion.info;

public class UniqueKeyError
    extends Error {

  public UniqueKeyError(final String message, final Object... parameters) {
    this(String.format(message, parameters));
  }

  public UniqueKeyError(final String message) {
    super(message);
  }

  public UniqueKeyError(final Throwable t) {
    super(t);
  }
}
