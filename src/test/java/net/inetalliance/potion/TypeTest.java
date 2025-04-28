package net.inetalliance.potion;

import com.ameriglide.phenix.core.Log;
import net.inetalliance.potion.info.Info;
import net.inetalliance.potion.query.Search;
import net.inetalliance.types.Currency;
import net.inetalliance.types.json.JsonMap;
import net.inetalliance.types.localized.LocalizedString;
import net.inetalliance.types.util.NetUtil;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import static net.inetalliance.potion.Locator.*;
import static org.junit.jupiter.api.Assertions.*;

public class TypeTest extends PotionTestCase {

  private static final Log log = new Log();

  private static void testReload(final int siteKey, final int productKey) {
    final GenericSite site = $(new GenericSite(siteKey));
    final GenericProduct selected = new GenericProduct(site, productKey);
    setRead(selected, false);
    read(selected);
    assertEquals("test product", selected.getName());
    final GenericSite reloadedSite = selected.getSite();
    assertEquals("test site", reloadedSite.getName());
    assertEquals("test site", reloadedSite.getName());
  }

  private static void testReload(final int siteKey, final int productKey, final int optionKey) {
    final GenericSite site = $(new GenericSite(siteKey));
    final GenericProduct product = $(new GenericProduct(site, productKey));
    final GenericOption option = $(new GenericOption(product, optionKey));
    assertEquals("test option", option.getName());
  }

  @Test
  public void testEnum() {
    try (final GenericSite site = new GenericSite()) {
      site.setName("test site");
      site.setUri(NetUtil.createUri("http", "www.playboy.com", 80));
      site.setIndustry(GenericSite.Industry.ADULT);
      create(user, site);
      log.debug(() -> "created %d".formatted(site.getKey()));
      try (final GenericSite selected = new GenericSite()) {
        selected.setKey(site.getKey());
        assertEquals(GenericSite.Industry.ADULT, selected.getIndustry());
      }
    }
  }

  @Test
  public void testLocale() {
    try (final GenericSite site = new GenericSite()) {
      site.setName("test site");
      site.setUri(NetUtil.createUri("http", "www.playboy.com", 80));
      site.setLocale(Locale.US);
      create(user, site);
      log.debug(() -> "created %d".formatted(site.getKey()));
      try (final GenericSite selected = new GenericSite()) {
        selected.setKey(site.getKey());
        setRead(selected, false);
        read(selected);
        assertEquals(Locale.US, selected.getLocale());
      }
    }
  }

  @Test
  public void testCurrency() {
    try (final GenericSite site = new GenericSite()) {
      site.setName("test site");
      site.setUri(NetUtil.createUri("http", "www.playboy.com", 80));
      final Currency handlingFee = new Currency(500.25D);
      site.setHandlingFee(handlingFee);
      create(user, site);
      log.debug(() -> "created %d".formatted(site.getKey()));
      try (final GenericSite selected = new GenericSite()) {
        selected.setKey(site.getKey());
        setRead(selected, false);
        read(selected);
        assertEquals(handlingFee, selected.getHandlingFee());
      }
    }
  }

  @Test
  public void testLocalizedString() {
    try (final GenericSite site = new GenericSite()) {
      site.setName("test site");
      site.setUri(NetUtil.createUri("http", "www.playboy.com", 80));
      final LocalizedString description = LocalizedString.$(Locale.US, "hello");
      site.setDescription(description);
      create(user, site);
      log.debug(() -> "created %d".formatted(site.getKey()));
      try (final GenericSite selected = new GenericSite()) {
        selected.setKey(site.getKey());
        setRead(selected, false);
        read(selected);
        assertEquals(description, selected.getDescription());
      }
    }
  }

  @Test
  public void testSubObject() {
    try (final GenericSite site = new GenericSite()) {
      site.setName("test site");
      site.setUri(NetUtil.createUri("http", "www.playboy.com", 80));
      final GenericRating rating = new GenericRating();
      rating.stars = 4;
      rating.review = "this product rocks";
      site.setRating(rating);
      create(user, site);
      log.debug(() -> "created %d".formatted(site.getKey()));
      try (final GenericSite selected = new GenericSite()) {
        selected.setKey(site.getKey());
        setRead(selected, false);
        read(selected);
        assertEquals("this product rocks", selected.rating.review);
        assertEquals(Integer.valueOf(4), selected.rating.stars);
      }
    }
  }

  @Test
  public void testNestedSubObject() {
    try (final GenericSite site = new GenericSite()) {
      site.setName("test site");
      site.setUri(NetUtil.createUri("http", "www.playboy.com", 80));
      final DescribedRating rating = new DescribedRating();
      rating.rating = new GenericRating();
      rating.rating.stars = 4;
      rating.rating.review = "this product rocks";
      rating.description = "test rating";
      site.setDescribed(rating);
      create(user, site);
      log.debug(() -> "created %d".formatted(site.getKey()));
      try (final GenericSite selected = new GenericSite()) {
        selected.setKey(site.getKey());
        setRead(selected, false);
        read(selected);
        assertEquals("this product rocks", selected.described.rating.review);
        assertEquals(Integer.valueOf(4), selected.described.rating.stars);
        assertEquals("test rating", selected.described.description);
      }
    }
  }

  @Test
  public void testForeignProperty() {
    try (final GenericSite site = new GenericSite()) {
      site.setName("test site");
      site.setUri(NetUtil.createUri("http", "www.playboy.com", 80));
      site.setIndustry(GenericSite.Industry.ADULT);
      final LocalizedString description = LocalizedString.$(Locale.US, "hello");
      site.setDescription(description);
      create(user, site);
      log.debug(() -> "created %d".formatted(site.getKey()));
      try (final GenericProduct product = new GenericProduct(site)) {
        product.setName("test product");
        create(user, product);
        testReload(site.getKey(), product.id);
        final JsonMap json = Info.$(product).toJson(product);
        assertTrue(json.containsKey("site"));
        assertEquals(site.getKey(), json.getInteger("site"));
      }
    }
  }

  @Test
  public void testTwoDeep() {
    try (final GenericSite site = new GenericSite()) {
      site.setName("test site");
      site.setUri(NetUtil.createUri("http", "www.playboy.com", 80));
      site.setIndustry(GenericSite.Industry.ADULT);
      final LocalizedString description = LocalizedString.$(Locale.US, "hello");
      site.setDescription(description);
      create(user, site);
      try (final GenericProduct product = new GenericProduct(site)) {
        product.setSite(site);
        product.setName("test product");
        create(user, product);
        try (final GenericOption option = new GenericOption(product)) {
          option.setProduct(product);
          option.setName("test option");
          create(user, option);
          testReload(site.getKey(), product.id, option.getKey());
        }
      }
    }
  }

  @Test
  public void testSearch() {
    try (final GenericSite noMatch = new GenericSite()) {
      noMatch.setName("no match for search");
      noMatch.setUri(NetUtil.createUri("http", "www.no.com", 80));
      create(user, noMatch);
      try (final GenericSite oneBMatch = new GenericSite()) {
        oneBMatch.setName("no match");
        oneBMatch.setShortDescription("foo");
        oneBMatch.setUri(NetUtil.createUri("http", "www.no.com", 80));
        create(user, oneBMatch);
        try (final GenericSite oneAMatch = new GenericSite()) {
          oneAMatch.setName("foo");
          oneAMatch.setUri(NetUtil.createUri("http", "www.no.com", 80));
          create(user, oneAMatch);
          try (final GenericSite twoMatches = new GenericSite()) {
            twoMatches.setName("foo");
            twoMatches.setUri(NetUtil.createUri("http", "www.no.com", 80));
            twoMatches.setShortDescription("foo bar!");
            create(user, twoMatches);
            final Set<GenericSite> matches = $$(new Search<>(GenericSite.class, "foo"));
            assertEquals(3, matches.size());
            assertFalse(matches.contains(noMatch));
            final Iterator<GenericSite> iterator = matches.iterator();
            assertEquals(twoMatches, iterator.next());
            assertEquals(oneAMatch, iterator.next());
            assertEquals(oneBMatch, iterator.next());
            final Set<GenericSite> andMatches = $$(
              new Search<>(GenericSite.class, "foo").and(GenericSite.withName("no match")));
            assertEquals(1, andMatches.size());
            assertEquals(oneBMatch, andMatches.iterator().next());
            final Set<GenericSite> andMatchesReverse = $$(
              GenericSite.withName("no match").and(new Search<>(GenericSite.class, "foo")));
            assertEquals(1, andMatchesReverse.size());
            assertEquals(oneBMatch, andMatchesReverse.iterator().next());
          }
        }
      }
    }
  }

  @Test
  public void testEmailSearch() throws URISyntaxException {
    try (final GenericSite s = new GenericSite()) {
      s.setName("AOL");
      s.setShortDescription("medaquip1@aol.com");
      s.setUri(new URI("https://www.aol.com"));
      create(user, s);
      for (final String q : Arrays.asList("medaquip1", "aol", "com")) {
        assertTrue(Locator.$$(new Search<>(GenericSite.class, q)).contains(s));
      }
    }
  }

}
