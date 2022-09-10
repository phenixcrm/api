package com.ameriglide.phenix.api;

import com.ameriglide.phenix.common.Objection;
import com.ameriglide.phenix.common.ObjectionCategory;
import com.ameriglide.phenix.common.ProductLine;
import com.ameriglide.phenix.model.ListableModel;
import com.ameriglide.phenix.model.Searchable;
import com.ameriglide.phenix.servlet.exception.NotFoundException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import net.inetalliance.potion.Locator;
import net.inetalliance.potion.info.Info;
import net.inetalliance.potion.query.Query;
import net.inetalliance.potion.query.Search;
import net.inetalliance.sql.DbVendor;
import net.inetalliance.sql.Namer;
import net.inetalliance.types.json.Json;

import static com.ameriglide.phenix.core.Strings.isNotEmpty;
import static net.inetalliance.sql.OrderBy.Direction.ASCENDING;

@WebServlet("/api/objection/*")
public class ObjectionModel
    extends ListableModel<Objection>
    implements Searchable<Objection> {

  public ObjectionModel() {
    super(Objection.class);
  }

  public static void main(String[] args) {
    for (final String s : Info.$(Objection.class).getCreate(DbVendor.POSTGRES, Namer.simple)) {
      System.out.println(s);
    }
  }

  @Override
  public Query<Objection> search(final String query) {
    return new Search<>(Objection.class, query.split(" "));
  }

  @Override
  public Query<Objection> all(final Class<Objection> type, final HttpServletRequest request) {

    Query<Objection> q = super.all(type, request);

    final String c = request.getParameter("c");
    if (isNotEmpty(c)) {
      final ObjectionCategory objectionCategory = Locator
          .$(new ObjectionCategory(Integer.valueOf(c)));
      if (objectionCategory == null) {
        throw new NotFoundException("Could not find objection category %s", c);
      }
      q = q.and(Objection.withCategory(objectionCategory));
    }
    final String p = request.getParameter("p");
    if (isNotEmpty(p)) {
      final ProductLine productLine = Locator.$(new ProductLine(Integer.valueOf(p)));
      if (productLine == null) {
        throw new NotFoundException("Could not find product line %s", p);
      }
      q = q.and(Objection.withProductLine(productLine));
    }

    return q.orderBy("id", ASCENDING);
  }

  @Override
  public Json toJson(final HttpServletRequest request, Objection arg) {
    return Info.$(arg).toJson(arg).$("categoryName", arg.getCategory().getName());
  }
}
