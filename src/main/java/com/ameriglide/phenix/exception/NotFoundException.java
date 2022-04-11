package com.ameriglide.phenix.exception;

import jakarta.servlet.http.HttpServletResponse;

public class NotFoundException
    extends PhenixServletException {

  public NotFoundException() {
    super(HttpServletResponse.SC_NOT_FOUND);
  }

  public NotFoundException(final String message, final Object... params) {
    super(HttpServletResponse.SC_NOT_FOUND, message, params);
  }
}
