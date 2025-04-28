package net.inetalliance.potion;

import com.ameriglide.phenix.core.Classes;
import com.ameriglide.phenix.core.IOStreams;
import com.ameriglide.phenix.core.Log;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.potion.info.Info;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static java.lang.reflect.Proxy.newProxyInstance;

public class MessageServer
		extends HttpServlet {

	private static final Log log = new Log();
	private static HttpClient httpClient;

	public MessageServer() {
	}

	@SuppressWarnings({"unchecked"})
	private static <T> Object read(final Class<T> type, final DataInputStream in)
			throws IOException, IllegalAccessException, InstantiationException, ClassNotFoundException,
			NoSuchMethodException,
			InvocationTargetException {
		if (in.readBoolean()) {
			if (type.equals(Boolean.class) || type.equals(boolean.class)) {
				return in.readBoolean();
			} else if (type.equals(Byte.class) || type.equals(byte.class)) {
				return in.readByte();
			} else if (type.equals(Character.class) || type.equals(char.class)) {
				return in.readChar();
			} else if (type.equals(Double.class) || type.equals(double.class)) {
				return in.readDouble();
			} else if (type.equals(Float.class) || type.equals(float.class)) {
				return in.readFloat();
			} else if (type.equals(Integer.class) || type.equals(int.class)) {
				return in.readInt();
			} else if (type.equals(Long.class) || type.equals(long.class)) {
				return in.readLong();
			} else if (type.equals(Short.class) || type.equals(short.class)) {
				return in.readShort();
			} else if (type.equals(String.class)) {
				return IOStreams.read(in);
			} else if (type.equals(LocalDateTime.class)) {
				return LocalDateTime.ofInstant(Instant.ofEpochMilli(in.readLong()), ZoneId.systemDefault());
			} else if (type.equals(LocalDate.class)) {
				return LocalDate.ofInstant(Instant.ofEpochMilli(in.readLong()), ZoneId.systemDefault());
			} else if (Collection.class.isAssignableFrom(type)) {
				final int size = in.readInt();
				final Collection collection = SortedSet.class.isAssignableFrom(type)
						? new TreeSet()
						: Set.class.isAssignableFrom(type) ? new HashSet(size) : new ArrayList(size);
				for (int i = 0; i < size; i++) {
					final Class<?> itemType = Class.forName(IOStreams.read(in));
					collection.add(read(itemType, in));
				}
				return collection;
			} else if (Map.class.isAssignableFrom(type)) {
				final int size = in.readInt();
				final Map map = new HashMap(size);
				for (int i = 0; i < size; i++) {
					final Class<?> keyType = Class.forName(IOStreams.read(in));
					final Object entryKey = read(keyType, in);
					final Class<?> valueType = Class.forName(IOStreams.read(in));
					final Object entryValue = read(valueType, in);
					map.put(entryKey, entryValue);
				}
				return map;
			} else {
				final Info<T> info = Info.$(type);
				final T value = type.getDeclaredConstructor().newInstance();
				info.readExternal(in, value);
				for (final Field collectionField : info.collections) {
					final Collection collection;
					if (in.readBoolean()) {
						final int size = in.readInt();
						collection = new ArrayList(size);
						for (int i = 0; i < size; i++) {
							final Class objectType = Classes.forName(IOStreams.read(in));
							collection.add(read(objectType, in));
						}
					} else {
						collection = null;
					}
					collectionField.set(value, collection);
				}
				for (final Field setField : info.subObjectSets) {
					final Set set;
					if (in.readBoolean()) {
						final int size = in.readInt();
						set = new HashSet(size);
						for (int i = 0; i < size; i++) {
							final Class objectType = Classes.forName(IOStreams.read(in));
							set.add(read(objectType, in));
						}
					} else {
						set = null;
					}
					setField.set(value, set);
				}
				for (final Field collectionField : info.subObjectCollections) {
					final Collection collection;
					if (in.readBoolean()) {
						final int size = in.readInt();
						collection = new ArrayList(size);
						for (int i = 0; i < size; i++) {
							final Class objectType = Classes.forName(IOStreams.read(in));
							collection.add(read(objectType, in));
						}
					} else {
						collection = null;
					}
					collectionField.set(value, collection);
				}
				return value;
			}
		} else {
			return null;
		}
	}

	@SuppressWarnings({"unchecked"})
	private static void write(final Class type, final Object value, final DataOutputStream out)
			throws IOException {
		if (value == null) {
			out.writeBoolean(false);
		} else {
			out.writeBoolean(true);
			if (type.equals(Boolean.class) || type.equals(boolean.class)) {
				out.writeBoolean((Boolean) value);
			} else if (type.equals(Byte.class) || type.equals(byte.class)) {
				out.writeByte((Byte) value);
			} else if (type.equals(Character.class) || type.equals(char.class)) {
				out.writeChar((Character) value);
			} else if (type.equals(Double.class) || type.equals(double.class)) {
				out.writeDouble((Double) value);
			} else if (type.equals(Float.class) || type.equals(float.class)) {
				out.writeFloat((Float) value);
			} else if (type.equals(Integer.class) || type.equals(int.class)) {
				out.writeInt((Integer) value);
			} else if (type.equals(Long.class) || type.equals(long.class)) {
				out.writeLong((Long) value);
			} else if (type.equals(Short.class) || type.equals(short.class)) {
				out.writeShort((Short) value);
			} else if (type.equals(String.class)) {
				IOStreams.write((String) value, out);
			} else if (LocalDateTime.class.equals(type)) {
				out.writeLong(((LocalDateTime) value).atZone(ZoneId.systemDefault()).toEpochSecond());
			} else if (LocalDate.class.equals(type)) {
				out.writeLong(((LocalDate) value).atStartOfDay().atZone(ZoneId.systemDefault()).toEpochSecond());
			} else if (Collection.class.isAssignableFrom(type)) {
				final Collection collection = (Collection) value;
				out.writeInt(collection.size());
				for (final Object item : collection) {
					if (item == null) {
						throw new NullPointerException(
								"You cannot return null collection items via MessageServer.");
					}
					final Class itemType = item.getClass();
					IOStreams.write(itemType.getName(), out);
					write(itemType, item, out);
				}
			} else if (Map.class.isAssignableFrom(type)) {
				final Map<?, ?> map = (Map) value;
				out.writeInt(map.size());
				for (final Map.Entry<?, ?> entry : map.entrySet()) {
					final Object entryKey = entry.getKey();
					if (entryKey == null) {
						throw new NullPointerException(
								"You cannot return map items with a null key via MessageServer.");
					}
					final Object entryValue = entry.getValue();
					if (entryValue == null) {
						throw new NullPointerException(
								"You cannot return map items with a null value via MessageServer.");
					}
					final Class keyType = entryKey.getClass();
					IOStreams.write(keyType.getName(), out);
					write(keyType, entryKey, out);
					final Class valueType = entryValue.getClass();
					IOStreams.write(valueType.getName(), out);
					write(valueType, entryValue, out);
				}
			} else {
				final Info info = Info.$(type);
				info.writeExternal(out, value);
				for (final Object collectionField : info.collections) {
					final Collection collection;
					try {
						collection = (Collection) ((Field) collectionField).get(value);
					} catch (IllegalAccessException e) {
						throw new RuntimeException(e);
					}
					out.writeBoolean(collection != null);
					if (collection != null) {
						out.writeInt(collection.size());
						for (final Object object : collection) {
							if (object == null) {
								throw new NullPointerException(
										"You cannot return null collection items via MessageServer.");
							}
							final Class objectType = object.getClass();
							IOStreams.write(objectType.getName(), out);
							write(objectType, object, out);
						}
					}
				}
				for (final Object setField : info.subObjectSets) {
					final Set set;
					try {
						set = (Set) ((Field) setField).get(value);
					} catch (IllegalAccessException e) {
						throw new RuntimeException(e);
					}
					out.writeBoolean(set != null);
					if (set != null) {
						out.writeInt(set.size());
						for (final Object object : set) {
							if (object == null) {
								throw new NullPointerException(
										"You cannot return null collection items via MessageServer.");
							}
							final Class objectType = object.getClass();
							IOStreams.write(objectType.getName(), out);
							write(objectType, object, out);
						}
					}
				}
				for (final Object collectionField : info.subObjectCollections) {
					final Collection collection;
					try {
						collection = (Collection) ((Field) collectionField).get(value);
					} catch (IllegalAccessException e) {
						throw new RuntimeException(e);
					}
					out.writeBoolean(collection != null);
					if (collection != null) {
						out.writeInt(collection.size());
						for (final Object object : collection) {
							if (object == null) {
								throw new NullPointerException(
										"You cannot return null collection items via MessageServer.");
							}
							final Class objectType = object.getClass();
							IOStreams.write(objectType.getName(), out);
							write(objectType, object, out);
						}
					}
				}
			}
		}
	}

	/**
	 * Returns a client facade for a specific server
	 *
	 * @param type the server facade interface
	 * @param uri  the uri to find the server
	 * @return a client
	 */
	public static <T> T $(final Class<T> type, final String uri) {
		try {
			return uri == null ? null : $(type, new URI(uri));
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Returns a client facade for a specific server
	 *
	 * @param type the server facade interface
	 * @param uri  the uri to find the server
	 * @return a client
	 */
	public static <T> T $(final Class<T> type, final URI uri) {
		log.debug("Setting up %s to send messages to %s", type.getName(), uri);
		httpClient = HttpClient.newHttpClient();
		return type
				.cast(newProxyInstance(type.getClassLoader(), new Class[]{type}, (proxy, method, args) -> {
					try {
						final ByteArrayOutputStream baos = new ByteArrayOutputStream();

						final DataOutputStream out = new DataOutputStream(baos);
						IOStreams.write(method.getName(), out);

						final Class<?>[] parameterTypes = method.getParameterTypes();
						log.debug(() -> "Sending call to %s/%s(%s)".formatted(uri, method.getName(),
								Arrays.stream(parameterTypes).map(Class::getName)));
						out.writeInt(parameterTypes.length);
						for (int i = 0; i < parameterTypes.length; i++) {
							IOStreams.write(parameterTypes[i].getName(), out);
							write(parameterTypes[i], args[i], out);
						}
						out.flush();
						var post =
								HttpRequest.newBuilder(uri)
										.POST(HttpRequest.BodyPublishers.ofByteArray(baos.toByteArray()))
										.setHeader("Content-type", "binary/octet-stream")
										.build();
						var response = httpClient.send(post, HttpResponse.BodyHandlers.ofInputStream());

						// read back response
						final int responseCode = response.statusCode();
						if (responseCode != SC_OK) {
							throw new RuntimeException("Server returned response code " + responseCode);
						}
						final Class<?> returnType = method.getReturnType();
						if (returnType.equals(Void.TYPE)) {
							return null;
						}
						try (DataInputStream in = new DataInputStream(response.body())) {
							final Object result = read(returnType, in);
							log.debug(() -> "Response received: %s".formatted(result));
							return result;
						}
					} catch (IOException e) {
						log.error(e);
						throw new RuntimeException(e);
					}
				}));
	}

	@SuppressWarnings({"unchecked"})
	@Override
	protected final void doGet(final HttpServletRequest request, final HttpServletResponse response)
			throws IOException {
		log.trace("Received request: %s", request.getRequestURI());
		final ServletInputStream inputStream = request.getInputStream();
		final DataInputStream in = new DataInputStream(inputStream);
		final String methodName = IOStreams.read(in);
		final int numParameters = in.readInt();
		final Class[] parameterTypes = new Class[numParameters];
		final Object[] arguments = new Object[numParameters];
		try {
			for (int i = 0; i < numParameters; i++) {
				parameterTypes[i] = Classes.forName(IOStreams.read(in));
				arguments[i] = read(parameterTypes[i], in);
			}
			final Method method = getClass().getMethod(methodName, parameterTypes);
			log.debug(() -> "Received call to %s.%s(%s)".formatted(getClass().getSimpleName(), methodName,
					Arrays.stream(parameterTypes).map(Class::getName)));
			final Object result = method.invoke(this, arguments);
			final Class<?> returnType = method.getReturnType();
			if (!returnType.equals(Void.TYPE)) {
				try (DataOutputStream out = new DataOutputStream(response.getOutputStream())) {
					write(returnType, result, out);
				} finally {
					log.trace("Serviced request: %s", request.getRequestURI());
				}
			}
		} catch (InvocationTargetException e) {
			log.error(e.getCause());
			throw new RuntimeException(e.getCause());
		} catch (Exception e) {
			log.error(e);
			throw new RuntimeException(e);
		}
	}

	@Override
	protected final void doPost(final HttpServletRequest request, final HttpServletResponse response)
			throws IOException {
		doGet(request, response);
	}

}
