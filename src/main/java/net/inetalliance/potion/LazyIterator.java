package net.inetalliance.potion;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A standard base class for lazy iterators.  The important thing about the lazy iterator behavior is that the next
 * element in the iteration is not calculated (no internal iterators are consumed) until the information is absolutely
 * needed.
 */
public abstract class LazyIterator<T> implements Iterator<T> {
	private boolean hasTestedNext;
	private T next;

	protected LazyIterator() {
		hasTestedNext = false;
		next = null;
	}

	// --------------------- Interface Iterator ---------------------
	@Override
	public final boolean hasNext() {
		if (!hasTestedNext)
			next = toNext();
		hasTestedNext = true;
		return next != null;
	}

	@Override
	public final T next() {
		if (hasTestedNext)
			hasTestedNext = false;
		else
			next = toNext();
		if (next == null)
			throw new NoSuchElementException();
		return next;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

	protected abstract T toNext();
}
