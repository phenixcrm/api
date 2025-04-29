package net.inetalliance.potion.obj;

import com.ameriglide.phenix.core.Optionals;
import com.ameriglide.phenix.core.Strings;
import lombok.Getter;
import lombok.Setter;
import net.inetalliance.potion.annotations.Persistent;
import net.inetalliance.potion.annotations.Searchable;
import net.inetalliance.types.annotations.MaxLength;
import net.inetalliance.types.annotations.PhoneNumber;
import net.inetalliance.types.geopolitical.Country;
import net.inetalliance.types.geopolitical.canada.Province;
import net.inetalliance.types.geopolitical.us.State;
import net.inetalliance.types.localized.MapLocalizedObject;

import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static net.inetalliance.potion.annotations.Searchable.Weight.B;
import static net.inetalliance.types.geopolitical.Country.UNITED_STATES;

@Getter
@Setter
@Persistent
public abstract class AddressPo {

    private static final String longPhone = "(%s) %s-%s";
    private static final String longPhoneWithOne = "1 (%s) %s-%s";
    private static final String shortPhone = "%s-%s";
    protected Province canadaDivision;
    @MaxLength(128)
    @Searchable(B)
    protected String city;
    @Searchable
    protected String name;
    @Searchable
    protected State state;
    @MaxLength(128)
    @Searchable(B)
    protected String street;
    @MaxLength(128)
    protected String street2;
    private Country country;
    @MaxLength(64)
    private String county;
    @MaxLength(64)
    @PhoneNumber
    @Searchable
    private String fax;
    @PhoneNumber
    @MaxLength(64)
    @Searchable
    private String phone;
    @PhoneNumber
    @MaxLength(64)
    @Searchable
    private String phone2;
    @MaxLength(64)
    private String postalCode;

    protected AddressPo() {
        country = UNITED_STATES;
    }

    public static String formatPhoneNumber(final String raw) {
        if (raw == null) {
            return "";
        }
        final int len = raw.length();
        return switch (len) {
            case 7 -> String.format(shortPhone, raw.substring(0, 3), raw.substring(3));
            case 10 -> String.format(longPhone, raw.substring(0, 3), raw.substring(3, 6), raw.substring(6));
            case 11 -> String.format(longPhoneWithOne, raw.substring(1, 4), raw.substring(4, 7), raw.substring(7));
            default -> raw;
        };
    }

    public static String unformatPhoneNumber(final String phone) {
        if (phone == null) {
            return null;
        }
        final int l = phone.length();
        final StringBuilder s = new StringBuilder(phone.length());
        for (int i = 0; i < l; i++) {
            final char c = phone.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                s.append(c);
            }
        }
        return s.toString();
    }

    @Override
    public String toString() {
        if (country == null || state == null) {
            return "(None)";
        }
        return (country == UNITED_STATES ?
                Stream.of(street, street2, city, "%s %s".formatted(state.getAbbreviation(), postalCode), county,
                        "Phone: %s".formatted(phone), "Phone2: %s".formatted(phone2), Strings.isEmpty(fax) ? null : "Fax: %s".formatted(fax))
                :
                Stream.of(street, street2, city, postalCode, county, Optionals.of(country.getLocalizedName())
                        .map(MapLocalizedObject::toString).orElse(null), phone, phone2, fax))
                .filter(Strings::isNotEmpty).collect(joining(", "));

    }
}
