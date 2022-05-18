package com.ameriglide.phenix.api;

import com.ameriglide.phenix.model.PhenixServlet;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.log.Log;

@WebServlet("/twilio/voice/status")
public class VoiceStatus extends PhenixServlet {
  @Override
  protected void post(HttpServletRequest request, HttpServletResponse response) throws Exception {
    request.getParameterMap().forEach((k, v) -> log.info("%s: %s", k, String.join(", ", v)));
    try (var reader = request.getReader()) {
      String line;
      while((line = reader.readLine())!= null) {
        log.info(line);
      }
    }
  }
  private static final Log log = Log.getInstance(VoiceStatus.class);
}
