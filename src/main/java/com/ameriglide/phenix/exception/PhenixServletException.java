package com.ameriglide.phenix.exception;

public abstract class PhenixServletException
    extends RuntimeException {

  public final int status;

  public PhenixServletException(final int status, final String message, final Object... params) {
    super(String.format(message, params));
    this.status = status;
  }

  public PhenixServletException(final int status, final Throwable cause, final String message,
                                final Object... params) {
    super(String.format(message, params), cause);
    this.status = status;
  }

  public PhenixServletException(final int status, final Throwable cause) {
    super(cause);
    this.status = status;
  }

  public PhenixServletException(final int status) {
    super();
    this.status = status;
  }
}
