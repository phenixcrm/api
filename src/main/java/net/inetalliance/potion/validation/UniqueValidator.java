package net.inetalliance.potion.validation;

import net.inetalliance.sql.Where;
import net.inetalliance.types.annotations.Unique;
import net.inetalliance.types.localized.LocalizedString;
import net.inetalliance.validation.validators.AnnotationValidator;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;

public class UniqueValidator
    extends AnnotationValidator<Unique> {

  public UniqueValidator() {
    super(1);
    register(Object.class, new UniqueTypeValidator<>() {
      @Override
      public State<Object> bind(final Unique annotation, final Class<?> objectType,
          final Class<?> fieldType,
          final String fieldName, final Function<String, String> propertyRenamer)
          throws Exception {
        final State<Object> state = super
            .bind(annotation, objectType, fieldType, fieldName, propertyRenamer);
        if (!state.caseSensitive) {
          throw new IllegalArgumentException(
              String.format("case insensitivity does not make sense for %s",
                  objectType.getSimpleName()));
        }
        return state;
      }

      @Override
      protected boolean different(final boolean caseSensitive, final Object fieldValue,
          final Object objectValue) {
        return UniqueValidator.different(fieldValue, objectValue, Object::equals);
      }

      @Override
      protected Where createWhere(final boolean caseSensitive, final String table,
          final String fieldName,
          final Object fieldValue) {
        return Where.eq(table, fieldName, fieldValue);
      }
    });
    register(String.class, new UniqueTypeValidator<>() {
      @Override
      protected boolean different(final boolean caseSensitive, final String fieldValue,
          final String objectValue) {
        return UniqueValidator.different(fieldValue, objectValue,
            caseSensitive ? String::equals : String::equalsIgnoreCase);
      }

      @Override
      protected Where createWhere(final boolean caseSensitive, final String table,
          final String fieldName,
          final String fieldValue) {
        return caseSensitive ? Where.eq(table, fieldName, fieldValue)
            : Where.like(table, fieldName, fieldValue,
                false);
      }
    });
    register(LocalizedString.class, new UniqueTypeValidator<>() {
      @Override
      protected boolean different(final boolean caseSensitive, final LocalizedString fieldValue,
          final LocalizedString objectValue) {
        return UniqueValidator.different(fieldValue, objectValue,
            caseSensitive ? Objects::equals : LocalizedString::equalsIgnoreCase);
      }

      @Override
      protected Where createWhere(final boolean caseSensitive, final String table,
          final String fieldName,
          final LocalizedString fieldValue) {
        return Where.and(caseSensitive
            ? fieldValue.createWheres(table, fieldName)
            : fieldValue.createCaseInsensitiveWheres(table, fieldName));
      }
    });
  }

  private static <T> boolean different(final T a, final T b,
      final BiPredicate<? super T, ? super T> equals) {
    return a == null || b == null || !equals.test(a, b);
  }
}
