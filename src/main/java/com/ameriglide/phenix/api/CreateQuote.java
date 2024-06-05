package com.ameriglide.phenix.api;

import com.ameriglide.phenix.common.Lead;
import com.ameriglide.phenix.core.Log;
import com.ameriglide.phenix.core.Strings;
import com.ameriglide.phenix.servlet.PhenixServlet;
import com.ameriglide.phenix.util.Publishing;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.potion.Locator;

@WebServlet("/api/createQuote")
public class CreateQuote extends PhenixServlet {
  @Override
  protected void get(final HttpServletRequest request, final HttpServletResponse response) throws Exception {
    var leadId = request.getParameter("leadId");
    var code = request.getParameter("code");
    if(Strings.isEmpty("leadId")) {
      log.warn(()->"received bad request to attach quote %s to lead %s".formatted(code,leadId));
      response.sendError(400,"leadId is required");
      return;
    }
    if(Strings.isEmpty("code")) {
      log.warn(()->"received bad request to attach quote %s to lead %s".formatted(code,leadId));
      response.sendError(400, "code is required");
      return;
    }
    try {
      var id = Integer.parseInt(leadId);
      var lead = Locator.$(new Lead(id));
      if(lead == null) {
        log.warn(()->"received request to attach quote %s to unknown lead %s".formatted(code,leadId));
        response.sendError(404, "could not find lead with id %d".formatted(id));
      } else {
        log.warn(()->"received request to attach quote %s to lead %s".formatted(code,leadId));
        Locator.update(lead,"CreateQuote",copy-> {
          copy.setQuote(code);
        });
        Publishing.quoteCreated(lead,log::error);
        response.sendError(200, "OK");
      }
    } catch(NumberFormatException e)  {
      response.sendError(400, "leadId must be a positive integer");
      log.error(e);
    }

  }
  private static final Log log = new Log();
}
