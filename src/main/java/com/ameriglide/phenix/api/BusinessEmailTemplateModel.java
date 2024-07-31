package com.ameriglide.phenix.api;

import com.ameriglide.phenix.common.Channel;
import com.ameriglide.phenix.model.ListableModel;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import net.inetalliance.potion.query.Query;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;

import java.util.regex.Pattern;

import static net.inetalliance.sql.OrderBy.Direction.ASCENDING;

@WebServlet("/api/businessEmailTemplate/*")
public class BusinessEmailTemplateModel extends ListableModel<Channel> {

    public BusinessEmailTemplateModel() {
        super(Channel.class, Pattern.compile("/api/businessEmailTemplate(?:/(\\d+))?"));
    }

    @Override
    public Query<Channel> all(final Class<Channel> type, final HttpServletRequest request) {

        Query<Channel> q = super.all(type, request);
        return q.orderBy("name", ASCENDING);
    }

    @Override
    public Json toJson(final HttpServletRequest request, Channel biz) {
        return new JsonMap().$("id", biz.id)
                .$("name", biz.getName())
                .$("emailTemplate", biz.getEmailTemplate())
                .$("emailTemplateStyles", biz.getEmailTemplateStyles());
    }
}
