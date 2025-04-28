package net.inetalliance.potion.validation;

import net.inetalliance.potion.Locator;
import net.inetalliance.potion.info.Info;
import net.inetalliance.potion.query.Query;
import net.inetalliance.sql.Namer;
import net.inetalliance.sql.Where;
import net.inetalliance.types.annotations.Unique;
import net.inetalliance.validation.Validator;
import net.inetalliance.validation.properties.Property;
import net.inetalliance.validation.types.TypeValidator;

import java.util.Locale;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;

public abstract class UniqueTypeValidator<T>
		implements TypeValidator<Unique, T, UniqueTypeValidator.State<T>> {

	@SuppressWarnings("unchecked")
	@Override
	public State<T> bind(final Unique annotation, final Class<?> objectType,
	                     final Class<? extends T> propertyType,
	                     final String fieldName, final Function<String, String> propertyRenamer)
			throws Exception {
		return new State<T>(
				net.inetalliance.validation.properties.Property.withName(objectType, fieldName, propertyType),
				annotation.caseSensitive());
	}

	@Override
	public String validate(final State<T> state, final Object object, final T propertyValue,
	                       final Locale locale,
	                       final boolean creating) {
		if (state.property == null || propertyValue == null) {
			return null;
		}

		final int matches = countMatches(state, object, propertyValue, creating);
		if (matches == 0) {
			return null;
		} else {
			final String objectWithArticle = Validator.messages.get(locale, object.getClass().getSimpleName());
			return Validator.messages.get(locale, "validation.unique", objectWithArticle);
		}
	}

	private <O> int countMatches(final State<T> state, final O object, final T propertyValue,
	                             final boolean creating) {
		return Locator
				.count(new Query<>(Info.$(object).type, o -> different(state.caseSensitive, propertyValue,
						state.property.get(o)) && !o.equals(object),
						(namer, table) -> {
							final String propertyName = state.property.getName();
							final Where where =
									createWhere(state.caseSensitive, table, propertyName, propertyValue);
							return creating ? where : andWhere(object, state, where, namer, table);
						}));

	}

	protected abstract boolean different(final boolean caseSensitive, final T fieldValue,
	                                     final T objectValue);

	protected abstract Where createWhere(final boolean caseSensitive, final String table,
	                                     final String fieldName,
	                                     final T fieldValue);

	private <O> Where andWhere(final O object, final State state, final Where where,
	                           final Namer namer,
	                           final String table) {
		final Info<O> info = Info.$(object);
		if (!info.get(state.property.getName()).isKey()) {
			return Where.and(where,
					Where.and(info.keys().map(p -> p.getWhere(namer, table, object)).collect(toList())).negate());
		}
		return where;
	}

	@Override
	public String toJavascript(final State<T> state, final Locale locale) {
		return null;
	}

	@Override
	public String toColumnCheck(final State<T> bound, final String name) {
		return null;
	}

	protected static class State<T> {

		protected final Property<? extends T> property;
		final boolean caseSensitive;

		public State(final Property<? extends T> property,
		             final boolean caseSensitive) {
			this.property = property;
			this.caseSensitive = caseSensitive;
		}
	}
}
