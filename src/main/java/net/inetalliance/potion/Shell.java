package net.inetalliance.potion;

import net.inetalliance.potion.info.Info;
import net.inetalliance.potion.info.PersistenceError;

import java.io.*;
import java.lang.reflect.InvocationTargetException;

import static net.inetalliance.potion.PropertyReference.forTypeName;

public class Shell<O>
		implements Externalizable {

	public Class<O> type;
	public O obj;

	public Shell() {
	}

	@SuppressWarnings({"unchecked"})
	public Shell(final O obj) {
		this.obj = obj;
		this.type = (Class<O>) obj.getClass();
	}

	@SuppressWarnings({"unchecked"})
	public static <O> O decode(final Object encoded) {
		return encoded == null ? null
				: encoded instanceof Shell ? (O) ((Shell) encoded).obj : (O) encoded;
	}

	public static <O> Serializable encode(final O obj) {
		return obj instanceof Serializable ? (Serializable) obj : new Shell<O>(obj);
	}

	@Override
	public void writeExternal(final ObjectOutput out)
			throws IOException {
		out.writeUTF(type.getName());
		Info.$(type).writeExternal(out, obj);
	}

	@SuppressWarnings({"unchecked"})
	@Override
	public void readExternal(final ObjectInput in)
			throws IOException, ClassNotFoundException {
		type = forTypeName(in.readUTF());
		try {
			obj = type.getDeclaredConstructor().newInstance();
			Info.$(type).readExternal(in, obj);
			Locator.setRead(obj, true);

		} catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
			throw new PersistenceError(e);
		}
	}
}
