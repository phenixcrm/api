package net.inetalliance.potion.info.columns;

import net.inetalliance.sql.Namer;
import net.inetalliance.sql.Where;
import net.inetalliance.types.Currency;
import net.inetalliance.types.json.JsonExpression;
import net.inetalliance.types.json.JsonMap;

import java.text.ParseException;

public class CurrencyColumn
        extends ProxyColumn<Long, Currency> {

    public CurrencyColumn(final String name, final boolean required, final boolean unique) {
        super(new LongColumn(name, required, unique));
    }

    @Override
    protected Currency parse(Long value) {
        return Currency.getInstance(value);
    }

    @Override
    protected Long format(Currency value) {
        return value.getValue();
    }

    @Override
    public Where getAutoCompleteWhere(final Namer namer, final String table, final String name,
                                      final String term) {
        try {
            return Where.eq(table, name, Currency.parse(term));
        } catch (ParseException e) {
            return null;
        }
    }

    @Override
    public Currency[] newArray(final int size) {
        return new Currency[size];
    }

    @Override
    protected void recordDefinitionAdditional(final JsonMap definition) {
        definition.put("type", "float");
        definition.put("align", "right");
        definition.put("renderer", new JsonExpression("Ext.util.Format.usMoney"));
    }
}
