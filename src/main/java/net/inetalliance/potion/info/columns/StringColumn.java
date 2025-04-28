package net.inetalliance.potion.info.columns;

import net.inetalliance.potion.info.Column;
import net.inetalliance.sql.DbVendor;
import net.inetalliance.sql.Namer;
import net.inetalliance.sql.SqlType;
import net.inetalliance.sql.Where;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;
import net.inetalliance.types.json.JsonString;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Pattern;

public class StringColumn extends Column<String> {
  public final Integer length;
  public final boolean xhtml;
  private final Integer xOut;
  private final Pattern xOutPattern;

  public StringColumn(final String name, final boolean required, final boolean unique, final Integer xOut,
                      final Integer length, final boolean xhtml) {
    super(name, required, unique);
    this.length = length;
    this.xhtml = xhtml;
    this.xOut = xOut;
    xOutPattern = xOut==null ? null:Pattern.compile("^[X]*.{%d}$".formatted(xOut));
  }

  private static String tidy(final String text) {
    return Jsoup.clean(text, Safelist.relaxed());
  }

  @Override
  public Json toJson(final String s) {
    if (s==null) {
      return null;
    }
    if (xOut==null || s.isEmpty()) {
      return new JsonString(s);
    } else {
      var builder = new StringBuilder(s.length());
      var numX = s.length() - xOut;
      int i;
      for (i = 0; i < numX; i++) {
        builder.append('X');
      }
      for (var j = i; j < s.length(); j++) {
        builder.append(s.charAt(j));
      }
      return new JsonString(builder.toString());
    }
  }

  @Override
  public Where getAutoCompleteWhere(final Namer namer, final String table, final String name, final String term) {
    return Where.like(table, name, String.format("%%%s%%", term), false);
  }

  @Override
  public String[] newArray(final int size) {
    return new String[size];
  }

  @Override
  public String read(final String name, final ResultSet r, final DbVendor vendor) throws SQLException {
    return r.getString(name);
  }

  @Override
  public String read(final Json json, final String previousValue) {
    var value = json.toString();
    if (value!=null && xOutPattern!=null && xOutPattern.matcher(value).matches()) {
      return previousValue; // don't save x'd out values
    } else {
      return xhtml ? tidy(value):value;
    }
  }

  @Override
  public String read(final DataInput in) throws IOException {
    return in.readUTF();
  }

  @Override
  public void write(final DataOutput out, final String s) throws IOException {
    out.writeUTF(s);
  }

  @Override
  public SqlType getType(final DbVendor vendor) {
    return length==null ? SqlType.sqlText:new SqlType(SqlType.sqlVarchar.javaType,
      String.format("%s(%d)", SqlType.sqlVarchar.name, length));
  }

  @Override
  public void bind(final PreparedStatement s, final DbVendor vendor, final Integer index, final String value) throws
    SQLException {
    s.setString(index, value);
  }

  @Override
  protected void recordDefinitionAdditional(final JsonMap definition) {
    definition.put("type", "string");
  }
}
