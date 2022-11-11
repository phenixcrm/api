package com.ameriglide.phenix.api;

import com.ameriglide.phenix.common.Team;
import com.ameriglide.phenix.model.ListableModel;
import jakarta.servlet.annotation.WebServlet;
import net.inetalliance.potion.query.Autocomplete;
import net.inetalliance.potion.query.Query;

@WebServlet("/api/team/*")
public class TeamModel extends ListableModel.Named<Team>{
  public TeamModel() {
    super(Team.class);
  }

  @Override
  public Query<Team> search(String query) {
    return new Autocomplete<>(Team.class,query.split(" "));
  }

}
