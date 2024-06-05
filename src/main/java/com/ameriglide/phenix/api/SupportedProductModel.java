package com.ameriglide.phenix.api;

import com.ameriglide.phenix.common.ServiceNote;
import com.ameriglide.phenix.common.SupportedProduct;
import com.ameriglide.phenix.core.Optionals;
import com.ameriglide.phenix.core.Strings;
import com.ameriglide.phenix.model.ListableModel;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import net.inetalliance.potion.Locator;
import net.inetalliance.potion.query.Query;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;

import static net.inetalliance.sql.OrderBy.Direction.DESCENDING;

@WebServlet("/api/supportedProduct/*")
public class SupportedProductModel extends ListableModel<SupportedProduct> {
  public SupportedProductModel() {
    super(SupportedProduct.class);
  }

  @Override
  public Query<SupportedProduct> all(final Class<SupportedProduct> type, final HttpServletRequest request) {
    var q = request.getParameter("q");
    Query<SupportedProduct> query;
    if(Strings.isEmpty(q)) {
      query = Query.all(SupportedProduct.class);
    } else {
      var noContacts =  SupportedProduct.withSerialNumberLike(q)
        .or(SupportedProduct.withTrackingLike(q))
        .or(SupportedProduct.withCarrierLike(q))
        .or(SupportedProduct.withProductLike(q));
      query = Locator.count(noContacts) > 0 ? noContacts :SupportedProduct.withContactLike(q);
    }
    return query.orderBy("added",DESCENDING);
  }

  @Override
  public Json toJson(final HttpServletRequest request, final SupportedProduct product) {
    return ((JsonMap) super.toJson(request, product))
      .$("customer", product.getLead().getContact().getFullName())
      .$("lastNote",
        Optionals.of(Locator.$1(ServiceNote.withSupportedProduct(product))).map(ServiceNote::getNote).orElse(""));
  }

  @Override
  public int getPageSize(final HttpServletRequest request) {
    int n = super.getPageSize(request);
    return n == 0 ? 25 : n;
  }
}
