package com.ameriglide.phenix;

import com.ameriglide.phenix.ws.Events;
import com.ameriglide.phenix.ws.SessionHandler;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import net.inetalliance.funky.Funky;
import net.inetalliance.funky.StringFun;
import net.inetalliance.log.Log;
import net.inetalliance.potion.Locator;
import net.inetalliance.potion.validation.NoLoopsValidator;
import net.inetalliance.potion.validation.UniqueValidator;
import net.inetalliance.sql.Db;
import net.inetalliance.types.annotations.NoLoops;
import net.inetalliance.types.annotations.Unique;
import net.inetalliance.validation.Validator;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.function.Supplier;

import static java.sql.DriverManager.deregisterDriver;
import static java.util.Collections.list;

@WebListener
public class Startup implements ServletContextListener {
  boolean development;

  private static final Supplier<Boolean> dev = Funky.runOnce(()-> System.getProperty("dev") != null);

  public static boolean isDevelopment() {
    return dev.get();
  }

	@Override
  public void contextInitialized(ServletContextEvent sce) {
    log.info("Le phénix s'est levé");
    Validator.register(NoLoops.class, new NoLoopsValidator());
    var context = sce.getServletContext();
    var path = context.getContextPath();
    this.development = System.getProperty("dev") != null;
    log.info("Starting up %s for %s", path +"/", development ? "development" : "production");
    final String dbParam = getContextParameter(context, "db");
    try {
      final Db db = new Db(new URI(dbParam));
      Class.forName(db.vendor.getDriver());
      Locator.attach(db);
    } catch (URISyntaxException e) {
      log.error("could not parse db parameter as uri: %s", dbParam, e);
      throw new RuntimeException(e);
    } catch (Throwable t) {
      log.error("could not attach to db", t);
      System.exit(1);
    }
    Validator.register(Unique.class, new UniqueValidator());
    SessionHandler.init();
    Events.handler = SessionHandler::new;
  }

  @Override
  public void contextDestroyed(ServletContextEvent sce) {
    Locator.detach();
    log.info("Deregistering JDBC drivers");
    list(DriverManager.getDrivers()).forEach(d -> {
      try {
        deregisterDriver(d);
      } catch (SQLException e) {
        log.error("failed to deregister %s", d.getClass().getName());
      }
    });
  }

  private String getContextParameter(final ServletContext context, final String key) {
    if(development) {
      var dev = context.getInitParameter("dev-" + key);
      if(StringFun.isNotEmpty(dev)) {
        return dev;
      }
    }
    return context.getInitParameter(key);
  }

  private static final transient Log log = Log.getInstance(Startup.class);
}
