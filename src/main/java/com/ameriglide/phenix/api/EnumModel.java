package com.ameriglide.phenix.api;

import com.ameriglide.phenix.core.Functions;
import com.ameriglide.phenix.core.Strings;
import com.ameriglide.phenix.servlet.PhenixServlet;
import com.ameriglide.phenix.servlet.exception.BadRequestException;
import com.ameriglide.phenix.servlet.exception.NotFoundException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.potion.Locator;
import net.inetalliance.types.Named;
import net.inetalliance.types.json.JsonList;
import net.inetalliance.types.json.JsonMap;
import net.inetalliance.types.localized.Localized;

import java.net.URLDecoder;
import java.util.EnumSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@WebServlet("/api/enum/*")
public class EnumModel
    extends PhenixServlet {

  private static final Pattern pattern = Pattern.compile("/api/v?\\d?/?enum/([^/]*)");

  private static final Function<Enum<?>, String> named;
  private static final Function<Enum<?>, String> localized;
  private static final Function<Enum<?>, String> vanilla;

  static {
    named = arg -> ((Named) arg).getName();
    localized = arg -> ((Localized) arg).getLocalizedName().toString();
    vanilla = Functions.memoize((Enum<?> arg) -> Strings.titlecase(arg.name().replace('_', ' ')),32);
  }

  @SuppressWarnings("unchecked")
  protected void get(final HttpServletRequest request, final HttpServletResponse response)
      throws Exception {
    final Matcher matcher = pattern.matcher(request.getRequestURI());
    if (matcher.find()) {
      final String[] types = URLDecoder.decode(matcher.group(1), java.nio.charset.StandardCharsets.UTF_8).split("[+]");
      final JsonMap result = new JsonMap();
      for (final String typeName : types) {
        final Class<? extends Enum<?>> type = (Class<? extends Enum<?>>) Locator.types.get(typeName);
        if (type == null) {
          throw new NotFoundException("Type \"%s\" not found.", typeName);
        } else if (!type.isEnum()) {
          throw new BadRequestException("%s is not an enum.", typeName);
        } else {
          @SuppressWarnings("rawtypes") final EnumSet<?> all = EnumSet.allOf((Class<? extends Enum>)type);
          final JsonList json = new JsonList(all.size());
          final Function<Enum<?>, String> namer;
          if (Localized.class.isAssignableFrom(type)) {
            namer = localized;
          } else if (Named.class.isAssignableFrom(type)) {
            namer = named;
          } else {
            namer = vanilla;
          }
          for (final Enum<?> value : all) {
            json.add(
                new JsonMap().$("ordinal", value.ordinal()).$("value", value.name())
                    .$("name", namer.apply(value)));
          }
          result.$(typeName, json);
        }
      }
      respond(response, result);
    } else {
      throw new BadRequestException("Invalid url format");
    }

  }

  public Pattern getPattern() {
    return pattern;
  }
}
