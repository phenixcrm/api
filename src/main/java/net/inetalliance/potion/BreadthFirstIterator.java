package net.inetalliance.potion;

import java.util.Iterator;
import java.util.List;

public abstract class BreadthFirstIterator<T> extends TreeIterator<T> {
	protected BreadthFirstIterator(final T initial) {
		super(initial);
	}

	protected BreadthFirstIterator(final Iterator<? extends T> initial) {
		super(initial);
	}

	@Override
	protected void addToOpen(final Iterator<? extends T> children, final List<Iterator<? extends T>> open) {
		// add to end
		open.add(children);
	}
}
