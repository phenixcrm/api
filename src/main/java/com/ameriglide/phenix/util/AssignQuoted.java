package com.ameriglide.phenix.util;

import com.ameriglide.phenix.Startup;
import com.ameriglide.phenix.common.Agent;
import com.ameriglide.phenix.common.Heat;
import com.ameriglide.phenix.common.Note;
import com.ameriglide.phenix.common.Opportunity;
import net.inetalliance.cli.Cli;
import net.inetalliance.potion.Locator;

import java.util.HashSet;

import static java.lang.System.out;

public class AssignQuoted implements Runnable {
  public static void main(String[] args) {
    Startup.bootstrap();
    try {
      Cli.run(new AssignQuoted(), args);
    } finally {
      Startup.teardown();
    }
  }

  @Override
  public void run() {
    var q = Opportunity.withAgent(Agent.system()).and(Opportunity.withHeat(Heat.QUOTED));
    var c = Locator.count(q);
    out.printf("There are %d quoted and unassigned%n".formatted(c));
    Locator.forEach(q, o -> {
      var humans = new HashSet<Agent>();
      Locator.forEach(Note.withOpportunity(o), n -> {
        if (n.getAuthor().id!=0) {
          humans.add(n.getAuthor());
        }
      });
      switch (humans.size()) {
        case 0 -> out.printf("[%d] no human contact%n", o.id);
        case 1 -> {
          var human = humans.iterator().next();
          out.printf("[%d] reassign to sole human %s%n", o.id, human.getFullName());
          Locator.update(o, "AssignQuoted", copy -> {
            copy.setAssignedTo(human);
          });
        }
        default -> out.printf("[%d] too many humans [%s]%n", o.id, humans.stream().map(Agent::getFullName).toList());
      }

    });

  }

  private static class Done extends RuntimeException {

  }
}
