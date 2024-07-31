package com.ameriglide.phenix.api;

import com.ameriglide.phenix.common.Channel;
import com.ameriglide.phenix.model.ListableModel;
import jakarta.servlet.annotation.WebServlet;
import net.inetalliance.potion.query.Autocomplete;
import net.inetalliance.potion.query.Query;

@WebServlet("/api/business/*")
public class BusinessModel extends ListableModel.Named<Channel>{
  public BusinessModel() {
    super(Channel.class);
  }

  @Override
  public Query<Channel> search(String query) {
    return new Autocomplete<>(Channel.class,query.split(" "));
  }

}
