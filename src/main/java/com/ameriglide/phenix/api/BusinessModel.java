package com.ameriglide.phenix.api;

import com.ameriglide.phenix.common.Business;
import com.ameriglide.phenix.model.ListableModel;
import jakarta.servlet.annotation.WebServlet;
import net.inetalliance.potion.query.Autocomplete;
import net.inetalliance.potion.query.Query;

@WebServlet("/api/business/*")
public class BusinessModel extends ListableModel.Named<Business>{
  public BusinessModel() {
    super(Business.class);
  }

  @Override
  public Query<Business> search(String query) {
    return new Autocomplete<>(Business.class,query.split(" "));
  }

}
