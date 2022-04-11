package com.ameriglide.phenix.exception;

import jakarta.servlet.http.HttpServletResponse;

public class UnauthorizedException
    extends PhenixServletException {

  public UnauthorizedException() {
    super(HttpServletResponse.SC_UNAUTHORIZED);
  }

  public UnauthorizedException(final String message, final Object... params) {
    super(HttpServletResponse.SC_UNAUTHORIZED, message, params);
  }
}
