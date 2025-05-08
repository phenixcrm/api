package net.inetalliance.potion.info.columns;

import net.inetalliance.potion.info.Column;
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

public abstract class ProxyColumn<F,T> extends Column<T> {
    private final Column<F> proxy;

    protected ProxyColumn(Column<F> proxy) {
        super(proxy.name, proxy.required, proxy.unique);
        this.proxy = proxy;
    }
    protected abstract T parse(F value);
    protected abstract F format(T value);

    @Override
    public Where getAutoCompleteWhere(Namer namer, String table, String name, String term) {
        return proxy.getAutoCompleteWhere(namer, table, name, term);
    }

    @Override
    public T read(String name, ResultSet r, DbVendor vendor) throws SQLException {
        return parse(proxy.read(name, r, vendor));
    }

    @Override
    public T read(Json json, T previousValue) {
        return parse(proxy.read(json, format(previousValue)));
    }

    @Override
    public T read(DataInput in) throws IOException {
        return parse(proxy.read(in));
    }

    @Override
    public void write(DataOutput out, T t) throws IOException {
        proxy.write(out, format(t));
    }

    @Override
    public SqlType getType(DbVendor vendor) {
        return proxy.getType(vendor);
    }

    @Override
    public void bind(PreparedStatement s, DbVendor vendor, Integer index, T t) throws SQLException {
        proxy.bind(s, vendor, index, format(t));
    }
}
