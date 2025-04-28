package net.inetalliance.potion;

import java.util.*;

public abstract class TreeIterator<T> extends LazyIterator<T> {
	/**
	 * a convenience value for leaf nodes to return
	 */
	protected final Iterator<T> none;
	private final List<Iterator<? extends T>> open;
	private final Set<T> closed;

	protected TreeIterator(final T initial) {
		this(Collections.singleton(initial).iterator());
	}

	protected TreeIterator(final Iterator<? extends T> initial) {
		open = new ArrayList<>(3);
		open.add(initial);
		closed = new HashSet<>(64);
		none = Collections.emptyIterator();
	}

	@Override
	protected T toNext() {
		while (true) {
			if (open.isEmpty()) {
				return null;
			}
			final Iterator<? extends T> current = open.get(0);
			if (current.hasNext()) {
				final T next = current.next();
				if (closed.add(next)) {
					addToOpen(getChildren(next), open);
					return next;
				}
			} else {
				open.remove(0);
			}
		}
	}

	protected abstract void addToOpen(final Iterator<? extends T> children, final List<Iterator<? extends T>> open);

	protected abstract Iterator<T> getChildren(final T object);

	public void clearClosed() {
		closed.clear();
	}
}
