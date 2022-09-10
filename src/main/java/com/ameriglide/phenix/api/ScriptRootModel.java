package com.ameriglide.phenix.api;

import com.ameriglide.phenix.common.ProductLine;
import com.ameriglide.phenix.common.ScriptNode;
import com.ameriglide.phenix.common.ScriptRoot;
import com.ameriglide.phenix.model.Key;
import com.ameriglide.phenix.model.Listable;
import com.ameriglide.phenix.model.TypeModel;
import com.ameriglide.phenix.servlet.exception.BadRequestException;
import com.ameriglide.phenix.servlet.exception.NotFoundException;
import com.ameriglide.phenix.types.ScriptNodeType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.potion.Locator;
import net.inetalliance.potion.info.Info;
import net.inetalliance.potion.query.Query;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScriptRootModel
    extends TypeModel<ScriptRoot> implements Listable<ScriptRoot> {

  private static final Pattern pattern = Pattern
      .compile("/api/productLine/(\\d+)/scriptRoot/?(\\d+)?");
  private final Info<ProductLine> productLineInfo;

  public ScriptRootModel() {
    super(ScriptRoot.class, pattern);
    this.productLineInfo = Info.$(ProductLine.class);
  }

  private static JsonMap json(final ScriptRoot root) {
    return new JsonMap().$("id", root.id)
        .$("productLine", root.getProductLine().id)
        .$("root", root.getRoot().id)
        .$("name", root.getName())
        .$("created", root.getCreated());
  }

  @Override
  public Json toJson(final HttpServletRequest request, ScriptRoot root) {
    return json(root);
  }

  @Override
  public Query<ScriptRoot> all(final Class<ScriptRoot> type,
                               final HttpServletRequest request) {
    final ProductLine productLine = getProductLine(request);
    return ScriptRoot.withProductLine(productLine);
  }

  private ProductLine getProductLine(final HttpServletRequest request) {
    final Matcher matcher = pattern.matcher(request.getRequestURI());
    if (matcher.find()) {
      final ProductLine productLine = productLineInfo.lookup(matcher.group(1));
      if (productLine == null) {
        throw new NotFoundException("Cannot find product line");
      }
      return productLine;
    } else {
      throw new BadRequestException("uri should match %s", pattern.pattern());
    }
  }

  @Override
  public JsonMap create(final Key<ScriptRoot> key,
                        final HttpServletRequest request,
                        final HttpServletResponse response, final JsonMap data) {
    ScriptNode root = Locator.$(new ScriptNode(data.getInteger("root")));
    if (root == null) {
      root = new ScriptNode();
      root.setType(ScriptNodeType.BRANCH);
      root.setPrompt("Start of script");
      Locator.create(request.getRemoteUser(), root);
    } else {
      root = root.duplicate(request.getRemoteUser());
    }
    return super.create(key, request, response, data.$("productLine", getProductLine(request).id)
        .$("root", root.id)
        .$("created", LocalDateTime.now()));
  }

  @Override
  protected Json toJson(final Key<ScriptRoot> key, final ScriptRoot root, final HttpServletRequest request) {
    return json(root);
  }

  @Override
  protected Key<ScriptRoot> getKey(final Matcher m) {
    return Key.$(ScriptRoot.class, URLDecoder.decode(m.group(2), StandardCharsets.UTF_8));
  }
}
