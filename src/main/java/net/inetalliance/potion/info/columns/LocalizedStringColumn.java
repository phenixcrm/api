package net.inetalliance.potion.info.columns;

import net.inetalliance.potion.info.Column;
import net.inetalliance.sql.DbVendor;
import net.inetalliance.sql.Namer;
import net.inetalliance.sql.SqlType;
import net.inetalliance.sql.Where;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;
import net.inetalliance.types.localized.LocalizedString;
import net.inetalliance.types.util.L10n;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.sql.*;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class LocalizedStringColumn
		extends Column<LocalizedString> {

	public LocalizedStringColumn(final String name, final boolean required, final boolean unique) {
		super(name, required, unique);
	}

	@Override
	public Json toJson(final LocalizedString string) {
		if (string == null) {
			return null;
		}
		final JsonMap map = new JsonMap();
		map.put("default", string.getDefaultLocale().toString());
		for (final Map.Entry<Locale, String> entry : string.entrySet()) {
			map.put(entry.getKey().toString(), entry.getValue());
		}
		return map;
	}

	@Override
	public Where getAutoCompleteWhere(final Namer namer, final String table, final String name,
	                                  final String term) {
		return Where.like(table, name, term, false);
	}

	@Override
	public LocalizedString[] newArray(final int size) {
		return new LocalizedString[size];
	}

	@Override
	public LocalizedString read(final String name, final ResultSet r, final DbVendor vendor)
			throws SQLException {
		if (vendor == DbVendor.POSTGRES) {
			final Array array = r.getArray(name);
			if (r.wasNull()) {
				return null;
			}

			final String[] strings = (String[]) array.getArray();
			final Locale defaultLocale = Locale.forLanguageTag(strings[0]);
			final Map<Locale, String> map = new HashMap<>(strings.length >> 1);
			for (int i = 1; i < strings.length; i += 2) {
				map.put(Locale.forLanguageTag(strings[i]), strings[i + 1]);
			}
			return new LocalizedString(defaultLocale, map);
		} else {
			final String s = r.getString(name);
			return r.wasNull() ? null : LocalizedString.$(L10n.locale, s);
		}
	}

	@Override
	public LocalizedString read(final Json json, final LocalizedString previousValue) {
		final JsonMap map = (JsonMap) json;
		final Locale defaultLocale = Locale.forLanguageTag(map.remove("default").toString());
		return new LocalizedString(defaultLocale, map.entrySet()
				.stream()
				.collect(Collectors.toMap(e -> Locale.forLanguageTag(e.getKey()),
						e -> e.getValue() == null
								? "null"
								: e.getValue().toString())));

	}

	@Override
	public LocalizedString read(final DataInput in)
			throws IOException {
		final LocaleColumn.Locale[] locales = LocaleColumn.Locale.values();
		final Locale defaultLocale = locales[in.readInt()].toLocale();
		final int size = in.readInt();
		final Map<Locale, String> strings = new HashMap<>(size);
		for (int i = 0; i < size; i++) {
			strings.put(locales[in.readInt()].toLocale(), in.readUTF());
		}
		return new LocalizedString(defaultLocale, strings);
	}

	@Override
	public void write(final DataOutput out, final LocalizedString localizedString)
			throws IOException {
		out.writeInt(LocaleColumn.Locale.fromLocale(localizedString.getDefaultLocale()).ordinal());
		out.writeInt(localizedString.size());
		for (final Map.Entry<Locale, String> entry : localizedString.entrySet()) {
			out.writeInt(LocaleColumn.Locale.fromLocale(entry.getKey()).ordinal());
			out.writeUTF(entry.getValue());
		}
	}

	@Override
	public SqlType getType(final DbVendor vendor) {
		return vendor == DbVendor.POSTGRES ? new SqlType(Types.ARRAY, "TEXT[]") : SqlType.sqlText;
	}

	@Override
	public void bind(final PreparedStatement s, final DbVendor vendor, final Integer index,
	                 final LocalizedString string)
			throws SQLException {
		if (vendor == DbVendor.POSTGRES) {
			s.setArray(index, toArray(s.getConnection(), string));
		} else {
			s.setString(index, string.toString());
		}
	}

	private static Array toArray(final Connection cxn, final LocalizedString string)
			throws SQLException {
		final String[] strings = new String[(string.size() << 1) + 1];
		strings[0] = string.getDefaultLocale().toString();
		int i = 1;
		for (final Map.Entry<Locale, String> entry : string.entrySet()) {
			strings[i++] = entry.getKey().toString();
			strings[i++] = entry.getValue();
		}
		return cxn.createArrayOf("text", strings);
	}
}
