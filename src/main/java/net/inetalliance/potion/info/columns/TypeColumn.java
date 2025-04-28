package net.inetalliance.potion.info.columns;

import net.inetalliance.potion.info.Column;
import net.inetalliance.potion.info.PersistenceError;
import net.inetalliance.sql.DbVendor;
import net.inetalliance.sql.Namer;
import net.inetalliance.sql.SqlType;
import net.inetalliance.sql.Where;
import net.inetalliance.types.json.Json;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class TypeColumn
		extends Column<Class> {

	public TypeColumn(final String name, final boolean required, final boolean unique) {
		super(name, required, unique);
	}

	@Override
	public Where getAutoCompleteWhere(final Namer namer, final String table, final String name,
	                                  final String term) {
		return Where.like(table, name, term, false);
	}

	@Override
	public Class[] newArray(final int size) {
		return new Class[size];
	}

	@Override
	public Class read(final String name, final ResultSet r, final DbVendor vendor)
			throws SQLException {
		final String raw = r.getString(name);
		try {
			return r.wasNull() ? null : Class.forName(raw);
		} catch (ClassNotFoundException e) {
			throw new PersistenceError(e);
		}
	}

	@Override
	public Class read(final Json json, final Class previousValue) {
		try {
			return Class.forName(json.toString());
		} catch (ClassNotFoundException e) {
			throw new PersistenceError(e);
		}
	}

	@Override
	public Class read(final DataInput in)
			throws IOException {
		try {
			return Class.forName(in.readUTF());
		} catch (ClassNotFoundException e) {
			throw new PersistenceError(e);
		}
	}

	@Override
	public void write(final DataOutput out, final Class type)
			throws IOException {
		out.writeUTF(type.getName());
	}

	@Override
	public SqlType getType(final DbVendor vendor) {
		return SqlType.sqlText;
	}

	@Override
	public void bind(final PreparedStatement s, final DbVendor vendor, final Integer index,
	                 final Class type)
			throws SQLException {
		s.setString(index, type.getName());
	}
}
