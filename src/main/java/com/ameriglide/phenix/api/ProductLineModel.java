package com.ameriglide.phenix.api;

import com.ameriglide.phenix.common.ProductLine;
import com.ameriglide.phenix.model.ListableModel;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/api/productLine/*")
public class ProductLineModel
    extends ListableModel.Named<ProductLine> {

  private final ScriptRootModel scriptRoot;

  public ProductLineModel() {
    super(ProductLine.class);
    this.scriptRoot = new ScriptRootModel();
  }

  @Override
  protected void service(final HttpServletRequest req, final HttpServletResponse resp)
      throws ServletException, IOException {
    if (scriptRoot.getPattern().matcher(req.getRequestURI()).matches()) {
      scriptRoot.service(req, resp);
    } else {
      super.service(req, resp);
    }
  }
}
