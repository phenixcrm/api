package com.ameriglide.phenix.api;

import com.ameriglide.phenix.Auth;
import com.ameriglide.phenix.common.Agent;
import com.ameriglide.phenix.common.Lead;
import com.ameriglide.phenix.servlet.PhenixServlet;
import com.ameriglide.phenix.servlet.exception.BadRequestException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.sql.DateTimeInterval;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonList;
import net.inetalliance.types.json.JsonMap;

import java.util.Objects;

import static com.ameriglide.phenix.common.Lead.withAgent;
import static com.ameriglide.phenix.core.Strings.isEmpty;
import static net.inetalliance.potion.Locator.forEach;
import static net.inetalliance.sql.OrderBy.Direction.ASCENDING;

@WebServlet("/api/freeBusy")
public class FreeBusy extends PhenixServlet {

  private static JsonMap toJson(final Lead lead) {
    return new JsonMap()
      .$("id", lead.id)
      .$("productLine", lead.getProductLine().getName())
      .$("reminder", lead.getReminder())
      .$("contact", lead.getContact().getLastNameFirstInitial())
      .$("channel", lead.getChannel().getName())
      .$("heat", lead.getHeat())
      .$("amount", lead.getAmount());
  }

  @Override
  protected void get(final HttpServletRequest request, final HttpServletResponse response) throws Exception {
    final boolean monthMode;
    final DateTimeInterval interval;
    final String day = request.getParameter("day");
    if (isEmpty(day)) {
      final String month = request.getParameter("month");
      if (isEmpty(month)) {
        throw new BadRequestException("Missing parameter \"month\"");
      } else {
        monthMode = true;
        var startOfMonth = Objects.requireNonNull(Json.parseDate(month)).withDayOfMonth(1).toLocalDate();
        var start = startOfMonth.minusDays(startOfMonth.getDayOfWeek().getValue() % 7).atStartOfDay();
        interval = new DateTimeInterval(start, start.plusDays(35));
      }
    } else {
      monthMode = false;
      var start = Objects.requireNonNull(Json.parseDate(day)).toLocalDate().atStartOfDay();
      interval = new DateTimeInterval(start, start.plusDays(1));
    }
    final Agent agent = Auth.getAgent(request);

    final JsonMap map = new JsonMap();
    forEach(Lead.withReminderIn(interval).and(withAgent(agent)).orderBy("reminder", ASCENDING), opp -> {
      if (monthMode) {
        final String day1 = Json.format(opp.getReminder().toLocalDate());
        JsonList list = map.getList(day1);
        if (list==null) {
          list = new JsonList();
          map.put(day1, list);
        }
        list.add(toJson(opp));
      } else {
        map.put(Json.format(opp.getReminder()), toJson(opp));
      }

    });
    respond(response, map);
  }
}
