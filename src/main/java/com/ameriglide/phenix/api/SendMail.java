package com.ameriglide.phenix.api;

import com.ameriglide.phenix.Auth;
import com.ameriglide.phenix.core.Log;
import com.ameriglide.phenix.core.Strings;
import com.ameriglide.phenix.servlet.PhenixServlet;
import jakarta.mail.internet.InternetAddress;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;
import net.inetalliance.util.mail.MailMessage;
import net.inetalliance.util.mail.PostOffice;

import java.io.UnsupportedEncodingException;

@WebServlet("/api/sendMail")
public class SendMail extends PhenixServlet {
    private static final Log log = new Log();

    public SendMail() {
        super();
    }

    @Override
    protected void post(HttpServletRequest request, HttpServletResponse response) throws Exception {
        var agent = Auth.getAgent(request);
        var email = JsonMap.parse(request.getInputStream());
        log.info(() -> Json.pretty(email));
        var msg = new MailMessage(toAddress(email
                .getList("from")
                .stream()
                .findFirst()
                .map(JsonMap.class::cast)
                .orElseGet(() -> new JsonMap().$("email", agent.getEmail()).$("name", agent.getFullName()))));
        var to = email.getList("to");
        if (to!=null) {
            to.stream().map(j -> (JsonMap) j).map(SendMail::toAddress).forEach(msg::addTo);
        }
        var cc = email.getList("cc");
        if (cc!=null) {
            cc.stream().map(j -> (JsonMap) j).map(SendMail::toAddress).forEach(msg::addCc);
        }
        var bcc = email.getList("bcc");
        if (bcc!=null) {
            bcc.stream().map(j -> (JsonMap) j).map(SendMail::toAddress).forEach(msg::addBcc);
        }
        msg.setSubject(email.get("subject"));
        var html = email.get("body");
        msg.setBody(Strings.stripTags(html), html);
        try {
            PostOffice.send(msg, "ameriglide.com");
        } catch (Throwable t) {
            log.error(t);
            response.sendError(500, t.getMessage());
        }
    }

    private static InternetAddress toAddress(JsonMap address) {
        try {
            return new InternetAddress(address.get("email"), address.get("name"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
