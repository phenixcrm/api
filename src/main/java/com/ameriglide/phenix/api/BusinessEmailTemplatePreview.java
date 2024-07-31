package com.ameriglide.phenix.api;

import com.ameriglide.phenix.common.Channel;
import com.ameriglide.phenix.servlet.PhenixServlet;
import com.ameriglide.phenix.servlet.exception.NotFoundException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.potion.Locator;
import net.inetalliance.types.www.ContentType;

import java.util.regex.Pattern;

@WebServlet("/api/businessEmailTemplatePreview/*")
public class BusinessEmailTemplatePreview
        extends PhenixServlet {

    private static final Pattern pattern = Pattern.compile("/api/businessEmailTemplatePreview/(\\d+)");

    public BusinessEmailTemplatePreview() {
    }

    public Pattern getPattern() {
        return pattern;
    }

    @Override
    protected void get(final HttpServletRequest request, final HttpServletResponse response)
            throws Exception {
        var matcher = pattern.matcher(request.getRequestURI());
        if (matcher.matches()) {
            var id = matcher.group(1);
            var biz = Locator.$(new Channel(Integer.parseInt(id)));
            if (biz == null) {
                throw new NotFoundException("Can't find that business");
            }
            var content = biz.getEmailTemplate() == null
                    ? ""
                    : String.format("<html><head>%s</head><body>%s</body></html>",
                    biz.getEmailTemplateStyles() == null
                            ? ""
                            : String.format("<style>%s</style>", biz.getEmailTemplateStyles()),
                    biz.getEmailTemplate());
            response.setContentLength(content.length());
            response.setContentType(ContentType.HTML.toString());
            try (var writer = response.getWriter()) {
                writer.write(content);
                writer.flush();
            }
        }
    }
}
