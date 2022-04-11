package com.ameriglide.phenix.exception;

import jakarta.servlet.http.HttpServletResponse;


public class ForbiddenException
    extends PhenixServletException {

  public ForbiddenException() {
    super(HttpServletResponse.SC_FORBIDDEN);
  }

  public ForbiddenException(final String message, final Object... params) {
    super(HttpServletResponse.SC_FORBIDDEN, message, params);
  }
}
