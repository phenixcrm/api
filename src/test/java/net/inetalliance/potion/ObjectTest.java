package net.inetalliance.potion;

import com.ameriglide.phenix.core.Log;
import net.inetalliance.types.localized.LocalizedString;
import net.inetalliance.types.util.NetUtil;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static net.inetalliance.potion.Locator.*;
import static org.junit.jupiter.api.Assertions.*;

public class ObjectTest
    extends PotionTestCase {

  private static final Log log = new Log();

  @Test
  public void testCreate() {
    try (var site = new GenericSite()) {
      site.setName("test site");
      site.setUri(NetUtil.createUri("http", "www.playboy.com", 80));
      Locator.create("junit", site);
      assertNotNull(site.getKey());
    }
  }

  @Test
  public void testUpdate() {
    try (var site = new GenericSite()) {
      site.setName("test site");
      site.setUri(NetUtil.createUri("http", "www.playboy.com", 80));
      create(user, site);
      assertNotNull(site.getKey());
      update(site, user, copy -> {
        copy.setName("test site - updated");
      });
      assertEquals("test site - updated", site.getName());
    }
  }

  @Test
  public void test$() {
    try (var site = new GenericSite()) {
      site.setName("test site");
      site.setUri(NetUtil.createUri("http", "www.playboy.com", 80));
      create(user, site);
      log.debug(()->"created %d".formatted(site.getKey()));
      assertEquals(site, $(new GenericSite(site.getKey())));
    }
  }

  @Test
  public void testDelete() {
    try (var site = new GenericSite()) {
      site.setName("test site");
      site.setUri(NetUtil.createUri("http", "www.playboy.com", 80));
      create(user, site);
      log.debug(()->"created %d".formatted(site.getKey()));
      delete(user, site);
      assertNull($(new GenericSite(site.getKey())));
    }
  }

  @Test
  public void testKeyChange() {
    try (var site = new GenericSite()) {
      site.setName("test site");
      site.setUri(NetUtil.createUri("http", "www.playboy.com", 80));
      site.setIndustry(GenericSite.Industry.ADULT);
      var description = LocalizedString.$(Locale.US, "hello");
      site.setDescription(description);
      create(user, site);
      try (var product = new GenericProduct(site)) {
        product.setSite(site);
        product.setName("test product");
        create(user, product);
        var newKey = site.getKey() + 500;
        update(site, user, copy -> {
          copy.setKey(newKey);
        });
        assertEquals(newKey, site.getKey().intValue());
        assertEquals(product.getSite().getKey().intValue(), newKey);
      }
    }
  }

  @Test
  public void testCascadeDelete() {
    try (var site = new GenericSite()) {
      site.setName("test site");
      site.setUri(NetUtil.createUri("http", "www.playboy.com", 80));
      site.setIndustry(GenericSite.Industry.ADULT);
      var description = LocalizedString.$(Locale.US, "hello");
      site.setDescription(description);
      create(user, site);
      try (var product = new GenericProduct(site)) {
        product.setSite(site);
        product.setName("test product");
        create(user, product);
        try (var sale = new GenericSale(site)) {
          sale.setProduct(product);
          create(user, sale);
          Locator.delete(user, product);
          assertNull($(new GenericSale(site, sale.getId())));
        }
      }
    }
  }

}
