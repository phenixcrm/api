package com.ameriglide.phenix.api;

import com.ameriglide.phenix.common.EmailTemplate;
import com.ameriglide.phenix.servlet.PhenixServlet;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.potion.Locator;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.inetalliance.types.www.ContentType.HTML;

@WebServlet("/api/emailTemplatePreview/*")
public class EmailTemplatePreview extends PhenixServlet {

    private static final String BASE_CSS = "body { font-family: \"Helvetica Neue\",Helvetica,Arial," + "sans-serif; }";
    private static final Pattern pattern = Pattern.compile("/api/emailTemplatePreview/(\\d+)");

    public EmailTemplatePreview() {
    }

    public Pattern getPattern() {
        return pattern;
    }

    @Override
    protected void get(final HttpServletRequest request, final HttpServletResponse response) throws Exception {
        final Matcher matcher = pattern.matcher(request.getRequestURI());
        if (matcher.matches()) {
            var id = matcher.group(1);
            var emailTemplate = Locator.$(new EmailTemplate(Integer.parseInt(id)));
            var content = emailTemplate==null ? "":String.format(
                    "<html><head><style>%s</style></head><body>%s</body></html>", BASE_CSS, emailTemplate.getText());
            response.setContentLength(content.length());
            response.setContentType(HTML.toString());
            try (var writer = response.getWriter()) {
                writer.write(content);
                writer.flush();
            }
        }
    }
}
