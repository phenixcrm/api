package net.inetalliance.potion;


import java.util.Iterator;
import java.util.function.Function;

public class FunctorBreadthFirstIterator<T>
		extends BreadthFirstIterator<T> {
	private final Function<T, Iterator<T>> functor;

	public FunctorBreadthFirstIterator(final T initial, final Function<T, Iterator<T>> functor) {
		super(initial);
		this.functor = functor;
	}

	public FunctorBreadthFirstIterator(final Iterable<T> initial,
	                                   final Function<T, Iterable<T>> functor) {
		// can't make another convenience constructor for iterables that takes a T initial because of method erasure.
		// just make your functor return an iterable or chain it with ToIterator like you see below.
		this(initial.iterator(), functor.andThen(Iterable::iterator));
	}

	public FunctorBreadthFirstIterator(final Iterator<T> initial,
	                                   final Function<T, Iterator<T>> functor) {
		super(initial);
		this.functor = functor;
	}

	@Override
	protected Iterator<T> getChildren(final T object) {
		return functor.apply(object);
	}
}
