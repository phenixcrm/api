package net.inetalliance.potion;

import com.ameriglide.phenix.core.Escaper;
import net.inetalliance.potion.info.Info;

import java.util.List;
import java.util.Objects;

import static com.ameriglide.phenix.core.Strings.isEmpty;
import static java.util.stream.Collectors.joining;

public class Hash<O>
		implements Comparable<Hash<O>> {

	public final Class<O> type;

	;
	private final String string;

	@SuppressWarnings({"unchecked"})
	public Hash(final O o) {
		if (o == null) {
			type = null;
			string = null;
		} else {
			type = (Class<O>) o.getClass();

			string = hash(o);
		}
	}

	private static String hash(Object o) {
		if (o == null) {
			return "null";
		}
		final Class<?> type = o.getClass();
		if (Info.isLocatable(type)) {
			return String.format("%s:%s", type.getSimpleName(), Info.keys(o)
					.filter(Objects::nonNull)
					.map(Object::hashCode)
					.map(Object::toString)
					.collect(joining(":")));
		} else {
			return Escaper.url.escape(o.toString());
		}
	}

	public Hash(final Class<O> type, final List<Object> keys) {
		this.type = type;
		this.string =
				keys.isEmpty() ? null
						: keys.stream().map(Object::toString).collect(joining(":", type.getSimpleName(), ""));
	}

	@Override
	public int hashCode() {
		return string != null ? string.hashCode() : 0;
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		final Hash hash = (Hash) o;

		return Objects.equals(string, hash.string);
	}

	@Override
	public String toString() {
		return string;
	}

	@Override
	public int compareTo(final Hash<O> hash) {
		return string.compareTo(hash.string);
	}

	public boolean isNull() {
		return isEmpty(string);
	}

}
