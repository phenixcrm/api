package net.inetalliance.potion.validation;

import net.inetalliance.potion.obj.SelfParenting;
import net.inetalliance.types.annotations.NoLoops;
import net.inetalliance.validation.Validator;
import net.inetalliance.validation.types.StatelessTypeValidator;
import net.inetalliance.validation.validators.AnnotationValidator;

import java.util.Locale;

import static net.inetalliance.potion.Locator.$;

public class NoLoopsValidator
		extends AnnotationValidator<NoLoops> {

	public NoLoopsValidator() {
		super(1);
		register(SelfParenting.class, new StatelessTypeValidator<>() {

			@Override
			public String toColumnCheck(final String name) {
				return null;
			}

			@Override
			public String toJavascript(final Locale locale) {
				return null;
			}

			@SuppressWarnings({"unchecked"})
			@Override
			public String validate(final Object object, final SelfParenting fieldValue,
			                       final Locale locale,
			                       final boolean creating) {
				if (fieldValue == null) {
					return null;
				}
				final SelfParenting selfParenting = (SelfParenting) $(object);
				final SelfParenting parent = $(fieldValue);
				if (parent != null && parent.isDescendantOf(selfParenting)) {
					return Validator.messages.get(locale, "validation.noLoops", parent.getName(), selfParenting.getName());
				} else {
					return null;
				}
			}
		});
	}
}
