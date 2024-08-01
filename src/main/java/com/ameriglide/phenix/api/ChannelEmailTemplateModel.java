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

@WebServlet("/api/channelEmailTemplate/*")
public class ChannelEmailTemplateModel extends ListableModel<Channel> {

    public ChannelEmailTemplateModel() {
        super(Channel.class, Pattern.compile("/api/channelEmailTemplate(?:/(\\d+))?"));
    }

    @Override
    public Query<Channel> all(final Class<Channel> type, final HttpServletRequest request) {

        Query<Channel> q = super.all(type, request);
        return q.orderBy("name", ASCENDING);
    }

    @Override
    public Json toJson(final HttpServletRequest request, Channel c) {
        return new JsonMap().$("id", c.id)
                .$("name", c.getName())
                .$("emailTemplate", c.getEmailTemplate())
                .$("emailTemplateStyles", c.getEmailTemplateStyles());
    }
}
