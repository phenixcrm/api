package net.inetalliance.potion.jdbc;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;

public class DebugPreparedStatement
    implements PreparedStatement {

  private String sql;

  public DebugPreparedStatement(final String sql) {
    this.sql = sql;
  }

  @Override
  public ResultSet executeQuery() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int executeUpdate() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setNull(final int i, final int sqlType) {
    addValue("NULL");
  }

  private void addValue(final String s) {
    sql = sql.replaceFirst("\\?", s);
  }

  @Override
  public void setBoolean(final int i, final boolean x) {
    addValue(Boolean.toString(x));
  }

  @Override
  public void setByte(final int i, final byte x) {
    addValue(Byte.toString(x));

  }

  @Override
  public void setShort(final int i, final short x) {
    addValue(Integer.toString(x));

  }

  @Override
  public void setInt(final int i, final int x) {
    addValue(Integer.toString(x));
  }

  @Override
  public void setLong(final int i, final long x) {
    addValue(Long.toString(x));

  }

  @Override
  public void setFloat(final int i, final float x) {
    addValue(Float.toString(x));
  }

  @Override
  public void setDouble(final int i, final double x) {
    addValue(Double.toString(x));
  }

  @Override
  public void setBigDecimal(final int i, final BigDecimal x) {
    addValue(x.toPlainString());

  }

  @Override
  public void setString(final int i, final String x) {
    addValue(String.format("'%s'", x));
  }

  @Override
  public void setBytes(final int i, final byte[] x) {
    throw new UnsupportedOperationException();

  }

  @Override
  public void setDate(final int i, final Date x) {
    addValue(String.format("'%s'", x));
  }

  @Override
  public void setTime(final int i, final Time x) {
    addValue(String.format("'%s'", x));
  }

  @Override
  public void setTimestamp(final int i, final Timestamp x) {
    addValue(String.format("'%s'", x));
  }

  @Override
  public void setAsciiStream(final int i, final InputStream x, final int length) {
    throw new UnsupportedOperationException();

  }

  @Override
  @Deprecated
  public void setUnicodeStream(final int i, final InputStream x, final int length) {
    throw new UnsupportedOperationException();

  }

  @Override
  public void setBinaryStream(final int i, final InputStream x, final int length) {
    throw new UnsupportedOperationException();

  }

  @Override
  public void clearParameters() {

  }

  @Override
  public void setObject(final int i, final Object x, final int targetSqlType) {
    addValue(String.format("'%s'", x));

  }

  @Override
  public void setObject(final int i, final Object x) {
    addValue(String.format("'%s'", x));

  }

  @Override
  public boolean execute() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addBatch() {
    throw new UnsupportedOperationException();

  }

  @Override
  public void setCharacterStream(final int i, final Reader reader, final int length) {
    throw new UnsupportedOperationException();

  }

  @Override
  public void setRef(final int i, final Ref x) {
    throw new UnsupportedOperationException();

  }

  @Override
  public void setBlob(final int i, final Blob x) {
    throw new UnsupportedOperationException();

  }

  @Override
  public void setClob(final int i, final Clob x) {
    throw new UnsupportedOperationException();

  }

  @Override
  public void setArray(final int i, final Array x) {
    throw new UnsupportedOperationException();

  }

  @Override
  public ResultSetMetaData getMetaData() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setDate(final int i, final Date x, final Calendar cal) {
    addValue(String.format("'%s'", x));

  }

  @Override
  public void setTime(final int i, final Time x, final Calendar cal) {
    addValue(String.format("'%s'", x));

  }

  @Override
  public void setTimestamp(final int i, final Timestamp x, final Calendar cal) {
    addValue(String.format("'%s'", x));

  }

  @Override
  public void setNull(final int i, final int sqlType, final String typeName) {
    addValue("NULL");

  }

  @Override
  public void setURL(final int i, final URL x) {
    addValue(String.format("'%s'", x));

  }

  @Override
  public ParameterMetaData getParameterMetaData() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setRowId(final int i, final RowId x) {
    throw new UnsupportedOperationException();

  }

  @Override
  public void setNString(final int i, final String x) {
    addValue(String.format("'%s'", x));

  }

  @Override
  public void setNCharacterStream(final int i, final Reader value, final long length) {
    throw new UnsupportedOperationException();

  }

  @Override
  public void setNClob(final int i, final NClob value) {
    throw new UnsupportedOperationException();

  }

  @Override
  public void setClob(final int i, final Reader reader, final long length) {
    throw new UnsupportedOperationException();

  }

  @Override
  public void setBlob(final int i, final InputStream inputStream, final long length) {
    throw new UnsupportedOperationException();

  }

  @Override
  public void setNClob(final int i, final Reader reader, final long length) {
    throw new UnsupportedOperationException();

  }

  @Override
  public void setSQLXML(final int i, final SQLXML xmlObject) {
    throw new UnsupportedOperationException();

  }

  @Override
  public void setObject(final int i, final Object x, final int targetSqlType,
      final int scaleOrLength) {
    throw new UnsupportedOperationException();

  }

  @Override
  public void setAsciiStream(final int i, final InputStream x, final long length) {
    throw new UnsupportedOperationException();

  }

  @Override
  public void setBinaryStream(final int i, final InputStream x, final long length) {
    throw new UnsupportedOperationException();

  }

  @Override
  public void setCharacterStream(final int i, final Reader reader, final long length) {
    throw new UnsupportedOperationException();

  }

  @Override
  public void setAsciiStream(final int i, final InputStream x) {
    throw new UnsupportedOperationException();

  }

  @Override
  public void setBinaryStream(final int i, final InputStream x) {
    throw new UnsupportedOperationException();

  }

  @Override
  public void setCharacterStream(final int i, final Reader reader) {
    throw new UnsupportedOperationException();

  }

  @Override
  public void setNCharacterStream(final int i, final Reader value) {
    throw new UnsupportedOperationException();

  }

  @Override
  public void setClob(final int i, final Reader reader) {
    throw new UnsupportedOperationException();

  }

  @Override
  public void setBlob(final int i, final InputStream inputStream) {
    throw new UnsupportedOperationException();

  }

  @Override
  public void setNClob(final int i, final Reader reader) {
    throw new UnsupportedOperationException();

  }

  @Override
  public ResultSet executeQuery(final String sql) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int executeUpdate(final String sql) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void close() {
    throw new UnsupportedOperationException();

  }

  @Override
  public int getMaxFieldSize() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setMaxFieldSize(final int max) {
    throw new UnsupportedOperationException();

  }

  @Override
  public int getMaxRows() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setMaxRows(final int max) {
    throw new UnsupportedOperationException();

  }

  @Override
  public void setEscapeProcessing(final boolean enable) {
    throw new UnsupportedOperationException();

  }

  @Override
  public int getQueryTimeout() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setQueryTimeout(final int seconds) {
    throw new UnsupportedOperationException();

  }

  @Override
  public void cancel() {
    throw new UnsupportedOperationException();

  }

  @Override
  public SQLWarning getWarnings() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clearWarnings() {
    throw new UnsupportedOperationException();

  }

  @Override
  public void setCursorName(final String name) {
    throw new UnsupportedOperationException();

  }

  @Override
  public boolean execute(final String sql) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ResultSet getResultSet() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getUpdateCount() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean getMoreResults() {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T unwrap(final Class<T> iface) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isWrapperFor(final Class<?> iface) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getFetchDirection() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setFetchDirection(final int direction) {
    throw new UnsupportedOperationException();

  }

  @Override
  public String toString() {
    return sql;
  }

  @Override
  public int getFetchSize() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setFetchSize(final int rows) {
    throw new UnsupportedOperationException();

  }

  @Override
  public int getResultSetConcurrency() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getResultSetType() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addBatch(final String sql) {
    throw new UnsupportedOperationException();

  }

  @Override
  public void clearBatch() {
    throw new UnsupportedOperationException();

  }

  @Override
  public int[] executeBatch() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Connection getConnection() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean getMoreResults(final int current) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ResultSet getGeneratedKeys() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int executeUpdate(final String sql, final int autoGeneratedKeys) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int executeUpdate(final String sql, final int[] columnIndexes) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int executeUpdate(final String sql, final String[] columnNames) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean execute(final String sql, final int autoGeneratedKeys) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean execute(final String sql, final int[] columnIndexes) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean execute(final String sql, final String[] columnNames) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getResultSetHoldability() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isClosed() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isPoolable() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setPoolable(final boolean poolable) {
    throw new UnsupportedOperationException();

  }

  @Override
  public void closeOnCompletion() {
    throw new UnsupportedOperationException();

  }

  @Override
  public boolean isCloseOnCompletion() {
    throw new UnsupportedOperationException();
  }


}
