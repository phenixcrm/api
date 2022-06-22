package com.ameriglide.phenix.api;

import com.ameriglide.phenix.Startup;
import com.ameriglide.phenix.servlet.PhenixServlet;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.types.json.JsonMap;

@WebServlet("/api/activity")
public class ActivityModel extends PhenixServlet {
  private final JsonMap activities = new JsonMap();
  public ActivityModel() {
    refresh();
  }

  private void refresh() {
    Startup.router.getActivities().forEach(a->activities.put(a.getSid(),a.getFriendlyName()));
  }

  @Override
  protected void get(HttpServletRequest request, HttpServletResponse response) throws Exception {
    respond(response,activities);
  }

  @Override
  public void destroy() {
    super.destroy();
    activities.clear();
  }
}
