package net.inetalliance.potion;

import io.zonky.test.db.postgres.embedded.DatabaseConnectionPreparer;
import io.zonky.test.db.postgres.junit5.EmbeddedPostgresExtension;
import io.zonky.test.db.postgres.junit5.PreparedDbExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.sql.Statement;

@SuppressWarnings("SqlNoDataSourceInspection")
public abstract class PotionTestCase {

  static final String user = "junit";

  @RegisterExtension
  public static final PreparedDbExtension pg = EmbeddedPostgresExtension
      .preparedDatabase((DatabaseConnectionPreparer) conn -> {
        try (final Statement statement = conn.createStatement()) {
          statement.execute("CREATE DATABASE Test");
        }
      });

  @BeforeEach
  public void attach() {
    System.out.println("Setting up Locator");
    Locator.attach(pg.getTestDatabase());
    Locator.register(GenericSite.class, GenericCategory.class, GenericProduct.class,
        GenericOption.class,
        GenericOrder.class, GenericSale.class, SimpleObject.class);
  }

  @AfterEach
  public void detach() {
    System.out.println("Tearing down Locator");
    Locator.detach();
  }
}
