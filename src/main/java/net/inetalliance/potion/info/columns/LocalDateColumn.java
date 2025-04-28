package net.inetalliance.potion.info.columns;

import com.ameriglide.phenix.core.Optionals;
import net.inetalliance.potion.info.Column;
import net.inetalliance.sql.DbVendor;
import net.inetalliance.sql.Namer;
import net.inetalliance.sql.SqlType;
import net.inetalliance.sql.Where;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;
import net.inetalliance.types.json.JsonString;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;


public class LocalDateColumn
        extends Column<LocalDate> {

    public LocalDateColumn(final String name, final boolean required, final boolean unique) {
        super(name, required, unique);
    }

    @Override
    public Json toJson(final LocalDate time) {
        return new JsonString(Json.format(time));
    }

    @Override
    public Where getAutoCompleteWhere(final Namer namer, final String table, final String name,
                                      final String term) {
        throw new UnsupportedOperationException(
                "Autocomplete is not supported for date midnight columns");
    }

    @Override
    public LocalDate[] newArray(final int size) {
        return new LocalDate[size];
    }

    @Override
    public LocalDate read(final String name, final ResultSet r, final DbVendor vendor)
            throws SQLException {
        final Timestamp timestamp = r.getTimestamp(name);
        return r.wasNull() ? null : timestamp.toLocalDateTime().toLocalDate();
    }

    @Override
    public LocalDate read(final Json json, final LocalDate previousValue) {
        return Optionals.of(Json.parseDate(json.toString())).map(LocalDateTime::toLocalDate).orElse(null);
    }

    @Override
    public LocalDate read(final DataInput in)
            throws IOException {
        return new Timestamp(in.readLong()).toLocalDateTime().toLocalDate();
    }

    @Override
    public void write(final DataOutput out, final LocalDate value)
            throws IOException {
        out.writeLong(Timestamp.valueOf(value.atStartOfDay()).getTime());
    }

    @Override
    public SqlType getType(final DbVendor vendor) {
        return SqlType.sqlTimestamp;
    }

    @Override
    public void bind(final PreparedStatement s, final DbVendor vendor, final Integer index,
                     final LocalDate value)
            throws SQLException {
        s.setTimestamp(index, Timestamp.valueOf(value.atStartOfDay()));
    }

    @Override
    protected void recordDefinitionAdditional(final JsonMap definition) {
        definition.put("dateFormat", "Y-m-d");
        definition.put("type", "date");
    }
}
