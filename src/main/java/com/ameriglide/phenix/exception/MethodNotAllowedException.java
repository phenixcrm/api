package com.ameriglide.phenix.exception;

import jakarta.servlet.http.HttpServletResponse;


public class MethodNotAllowedException
    extends PhenixServletException {

  public MethodNotAllowedException() {
    super(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
  }
}
