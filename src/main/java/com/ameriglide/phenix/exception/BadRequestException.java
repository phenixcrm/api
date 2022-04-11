package com.ameriglide.phenix.exception;

import jakarta.servlet.http.HttpServletResponse;


public class BadRequestException
    extends PhenixServletException {

  public BadRequestException(final String message, final Object... params) {
    super(HttpServletResponse.SC_BAD_REQUEST, message, params);
  }
}
