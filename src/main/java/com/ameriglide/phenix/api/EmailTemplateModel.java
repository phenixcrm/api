package com.ameriglide.phenix.api;

import com.ameriglide.phenix.common.EmailTemplate;
import com.ameriglide.phenix.model.ListableModel;
import com.ameriglide.phenix.model.Searchable;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import net.inetalliance.potion.info.Info;
import net.inetalliance.potion.query.Query;
import net.inetalliance.potion.query.Search;
import net.inetalliance.types.json.Json;

import static net.inetalliance.sql.OrderBy.Direction.ASCENDING;

@WebServlet("/api/emailTemplate/*")
public class EmailTemplateModel extends ListableModel<EmailTemplate> implements Searchable<EmailTemplate> {

    public EmailTemplateModel() {
        super(EmailTemplate.class);
    }

    @Override
    public Query<EmailTemplate> search(final String query) {
        return new Search<>(EmailTemplate.class, query.split("[ ]"));
    }

    @Override
    public Query<EmailTemplate> all(final Class<EmailTemplate> type,
                                    final HttpServletRequest request) {

        Query<EmailTemplate> q = super.all(type, request);
        return q.orderBy("name", ASCENDING);
    }

    @Override
    public Json toJson(final HttpServletRequest request, final EmailTemplate template) {
        return Info.$(template).toJson(template);
    }
}
