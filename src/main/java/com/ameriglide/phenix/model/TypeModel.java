package com.ameriglide.phenix.model;


import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TypeModel<T> extends Model<T> {

  protected final Class<T> type;

  protected TypeModel(final Class<T> type, final Pattern pattern) {
    super(pattern);
    this.type = type;
  }

  @Override
  protected Key<T> getKey(final Matcher m) {
    return Key.$(type, URLDecoder.decode((m.group(1)), StandardCharsets.UTF_8));
  }
}
