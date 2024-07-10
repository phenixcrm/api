package com.ameriglide.phenix.api;

import com.ameriglide.phenix.common.Agent;
import com.ameriglide.phenix.common.Heat;
import com.ameriglide.phenix.common.Note;
import com.ameriglide.phenix.common.Lead;
import com.ameriglide.phenix.core.Log;
import com.ameriglide.phenix.core.Strings;
import com.ameriglide.phenix.servlet.PhenixServlet;
import com.ameriglide.phenix.util.Publishing;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.potion.Locator;

import java.io.IOException;
import java.time.LocalDateTime;

@WebServlet(value = {"/api/qualify"})
public class Qualify extends PhenixServlet {
    private static final Log log = new Log();

    @Override
    protected void get(final HttpServletRequest request, final HttpServletResponse response) throws Exception {
        var rawId = request.getParameter("id");
        if (Strings.isEmpty(rawId)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "missing id");
            return;
        }
        var rawHeat = request.getParameter("heat");
        if (Strings.isEmpty(rawHeat)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "missing heat");
            return;
        }
        try {
            var id = Integer.parseInt(rawId);
          var lead = Locator.$(new Lead(id));
            if (lead==null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            var heat = Heat.valueOf(rawHeat.toUpperCase());
            Locator.update(lead, "Qualify", copy -> {
                copy.setHeat(heat);
                copy.setScreened(LocalDateTime.now());
                switch (heat) {
                    case DEAD -> log.info(() -> "Callcenter trashed %d".formatted(id));
                    case NEW -> log.info(() -> "Callcenter marked %d as new".formatted(id));
                    case CONTACTED -> log.info(()->"Callcenter qualified %d".formatted(id));
                    default -> {
                        try {
                            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "did not understand instructions");
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                var note = request.getParameter("note");
                if (Strings.isNotEmpty(note)) {
                    var n = new Note();
                    n.setAuthor(Agent.system());
                    n.setLead(copy);
                    n.setNote("Screening Note: " + note);
                    Locator.create("Publishing", n);
                }
                Publishing.newLead(copy);
            });
            if(heat == Heat.CONTACTED) {
                var call = CreateLead.dispatch(lead);
                log.info(() -> "Callcenter qualified %d [%s]".formatted(id, call.sid));
            }
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "%s is not a number".formatted(rawId));
        }
    }
}
