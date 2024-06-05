package com.ameriglide.phenix.api;

import com.ameriglide.phenix.common.Contact;
import com.ameriglide.phenix.common.Lead;
import com.ameriglide.phenix.model.Key;
import com.ameriglide.phenix.model.TypeModel;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import net.inetalliance.potion.info.Info;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonList;
import net.inetalliance.types.json.JsonMap;

import static com.ameriglide.phenix.common.Heat.SOLD;
import static java.util.regex.Pattern.compile;
import static net.inetalliance.potion.Locator.forEach;

@WebServlet("/api/relatedLeads/*")
public class RelatedLeadsModel
    extends TypeModel<Lead> {

  public RelatedLeadsModel() {
    super(Lead.class, compile("/api/relatedLeads/(.*)"));
  }

  @Override
  protected Json toJson(final Key<Lead> key, final Lead lead,
                        final HttpServletRequest request) {
    final JsonList list = new JsonList();
    final Contact contact = lead.getContact();
    forEach(Contact.withPhoneNumberIn(contact).join(Lead.class, "contact"), arg -> {
      if (!arg.id.equals(lead.id)) {
        final JsonMap map = new JsonMap().$("id").$("stage").$("amount").$("created")
            .$("estimatedClose");
        if (arg.getHeat() == SOLD) {
          map.$("saleDate");
        }
        Info.$(arg).fill(arg, map);
        map.$("business", arg.getBusiness().getAbbreviation())
            .$("assignedTo", arg.getAssignedTo().getLastNameFirstInitial())
            .$("productLine", arg.getProductLine().getName());
        list.add(map);
      }
    });
    return list;
  }
}
