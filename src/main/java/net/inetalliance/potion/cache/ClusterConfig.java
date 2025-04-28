package net.inetalliance.potion.cache;

import com.ameriglide.phenix.core.Log;
import net.inetalliance.file.xml.EnumSaxParser;
import net.inetalliance.file.xml.SaxTagHandler;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

public class ClusterConfig {

  private static transient final Log log = new Log();
  private State development;
  private State production;
  private transient State reading;

  public ClusterConfig() {
    super();
    development = new State();
    production = new State();
    try {
      final InputStream stream = ClusterConfig.class.getResourceAsStream("/cluster-config.xml");
      if (stream == null) {
        development.nodes.add(new InetSocketAddress("127.0.0.1", 11211));
        production.nodes.add(new InetSocketAddress("127.0.0.1", 11211));
        log.warn(()->"no cluster-config.xml found, defaulting to one node on localhost:11211");
      } else {
        new EnumSaxParser<>(Tag.class, null) {
          @Override
          protected ClusterConfig create() {
            return ClusterConfig.this;
          }
        }.load(stream);
      }
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  public List<InetSocketAddress> getNodes() {
    return System.getProperty("dev") != null ? development.nodes : production.nodes;
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public static enum Tag
      implements SaxTagHandler<ClusterConfig> {
    DEVELOPMENT {
      @Override
      public void open(final ClusterConfig state) {
        state.reading = state.development;
      }

      @Override
      public void close(final ClusterConfig state) {
        state.reading = null;
      }
    },
    PRODUCTION {
      @Override
      public void open(final ClusterConfig state) {
        state.reading = state.production;
      }

      @Override
      public void close(final ClusterConfig state) {
        state.reading = null;
      }
    },
    NODE {
      @Override
      public void open(final ClusterConfig state) {
        if (state.reading == null) {
          throw new IllegalArgumentException(
              "must be inside a <development> or <production> block");
        }
        state.reading.node = "localhost";
        state.reading.port = 11211;
      }

      @Override
      public void contents(final ClusterConfig state, final String contents) {
        state.reading.node = contents;
      }

      @Override
      public void attribute(final ClusterConfig state, final String name, final String value) {
        if ("port".equals(name)) {
          state.reading.port = Integer.valueOf(value);
        } else {
          super.attribute(state, name, value);
        }
      }

      @Override
      public void close(final ClusterConfig state) {
        state.reading.nodes.add(new InetSocketAddress(state.reading.node, state.reading.port));
      }
    };

    @Override
    public void contents(final ClusterConfig state, final String contents) {

    }

    @Override
    public void attribute(final ClusterConfig state, final String name, final String value) {
      throw new IllegalArgumentException(
          String.format("unknown attribute %s for tag %s", name, name()));
    }

  }

  private static final class State {

    private final List<InetSocketAddress> nodes;
    private transient String node;
    private transient int port;

    private State() {
      this.nodes = new ArrayList<InetSocketAddress>(1);
    }
  }

}
