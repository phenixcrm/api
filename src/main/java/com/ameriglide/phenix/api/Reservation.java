package com.ameriglide.phenix.api;

import com.ameriglide.phenix.Auth;
import com.ameriglide.phenix.Startup;
import com.ameriglide.phenix.common.Call;
import com.ameriglide.phenix.common.Leg;
import com.ameriglide.phenix.servlet.PhenixServlet;
import com.ameriglide.phenix.servlet.exception.BadRequestException;
import com.ameriglide.phenix.servlet.exception.NotFoundException;
import com.ameriglide.phenix.types.WorkerState;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.potion.Locator;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

import static net.inetalliance.potion.Locator.$1;

@WebServlet("/api/reservation/*")
public class Reservation extends PhenixServlet {
  private static final Pattern pattern = Pattern.compile("/api/reservation/(.*)/(accept|reject)");
  @Override
  protected void get(final HttpServletRequest request, final HttpServletResponse response) throws Exception {
    var loggedIn = Auth.getAgent(request);
    var m = pattern.matcher(request.getRequestURI());
    if(m.matches()) {
      var task = m.group(1);
      var call = Locator.$(new Call(task));
      if(call == null)  {
        throw new NotFoundException("could not find call %s",task);
      }
      var leg = $1(Leg.withCall(call).and(Leg.withAgent(loggedIn)));
      if(leg == null) {
        throw new NotFoundException("could not find leg for %s on call %s", loggedIn.getFullName(),task);
      }
      switch(m.group(2)) {
        case "accept" -> {
          if (Startup.router.acceptReservation(call.sid, leg.sid)) {
            Locator.update(leg,"Reservation", copy -> {
              copy.setAnswered(LocalDateTime.now());
            });
            Locator.update(call,"Reservation",copy-> {
              copy.setAgent(loggedIn);
            });
            Locator.update(call.getOpportunity(),"Reservation",copy-> {
              copy.setAssignedTo(loggedIn);
            });
            response.sendError(HttpServletResponse.SC_OK);
            Startup.router.setActivity(loggedIn, WorkerState.AVAILABLE.activity());
          } else {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
          }
        }
        case "reject" -> Startup.router.rejectReservation(call.sid,leg.sid);
      }
    } else {
      throw new BadRequestException("request much match %s", pattern.pattern());
    }
  }
}
