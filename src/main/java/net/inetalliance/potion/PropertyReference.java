package net.inetalliance.potion;


import net.inetalliance.potion.info.Info;
import net.inetalliance.potion.info.PersistenceError;
import net.inetalliance.potion.info.Property;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;
import java.util.stream.Stream;

public class PropertyReference<O>
		implements Externalizable {

	public String property;
	public String reference;
	public Class<O> type;

	@SuppressWarnings({"unchecked"})
	public PropertyReference(final String property, final O reference) {
		this.property = property;
		this.type = (Class<O>) reference.getClass();
		final Hash<O> hash = new Hash<O>(reference);
		this.reference = hash.toString();
	}

	public PropertyReference() {
	}

	@Override
	public int hashCode() {
		int result = property != null ? property.hashCode() : 0;
		result = 31 * result + (reference != null ? reference.hashCode() : 0);
		return result;
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		final PropertyReference reference1 = (PropertyReference) o;

		return Objects.equals(property, reference1.property) && Objects
				.equals(reference, reference1.reference);
	}

	public O get() {
		return Locator.getReference(type, reference);
	}

	public void setNull() {
		if (reference != null) {
			final O o = get();
			if (o != null) {
				try {
					final Property<O, ?> property = getProperty();
					property.field.set(o, null);
					Locator.updateReference(type, reference, o);
				} catch (IllegalAccessException e) {
					throw new PersistenceError(e);
				}
			}
		}
	}

	public Property<O, ?> getProperty() {
		return Info.$(type).properties().filter(p -> getProperty().getName().equals(p.getName()))
				.findFirst().orElse(null);
	}

	@Override
	public void writeExternal(final ObjectOutput out) {
		Stream.of(property, reference, type.getName()).forEach(s -> {
			try {
				out.writeUTF(s);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
	}

	@Override
	public void readExternal(final ObjectInput in)
			throws ClassNotFoundException {
    try {
      property = in.readUTF();
      reference = in.readUTF();
      type = forTypeName(in.readUTF());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
	}

	@SuppressWarnings("unchecked")
	public static <O> Class<O> forTypeName(final String typeName)
			throws ClassNotFoundException {

		Class<O> type = (Class<O>) Class.forName(typeName);
		if (type == null) {
			throw new IllegalStateException(String.format("you need to register %s", typeName));
		}
		return type;

	}
}
