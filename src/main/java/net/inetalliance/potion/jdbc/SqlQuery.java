package net.inetalliance.potion.jdbc;

import java.sql.PreparedStatement;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class SqlQuery {

  public final String sql;
  public final Iterable<BiConsumer<PreparedStatement, Integer>> parameters;

  public SqlQuery(final String sql) {
    this(sql, Collections.emptyList());
  }

  public SqlQuery(final String sql,
      final Iterable<BiConsumer<PreparedStatement, Integer>> parameters) {
    this.sql = sql;
    this.parameters = parameters;
  }

  public String toString() {
    final DebugPreparedStatement debug = new DebugPreparedStatement(sql);
    final AtomicInteger i = new AtomicInteger(1);
    for (BiConsumer<PreparedStatement, Integer> parameter : parameters) {
      parameter.accept(debug, i.getAndIncrement());
    }
    return debug.toString();
  }
}
