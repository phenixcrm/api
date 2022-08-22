package com.ameriglide.phenix;

import com.ameriglide.phenix.ws.Events;
import com.ameriglide.phenix.ws.HudHandler;
import com.ameriglide.phenix.ws.SessionHandler;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.annotation.WebListener;

@WebListener
public class Startup extends com.ameriglide.phenix.servlet.Startup {

    public Startup() {

    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        super.contextInitialized(sce);
        SessionHandler.init(router);
        Events.handler = SessionHandler::new;

    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        super.contextDestroyed(sce);
        HudHandler.shutdown();

    }

}
