package com.github.kno10.wikipediaentities.util;

import gnu.trove.set.hash.THashSet;

/**
 * This hash set is designed to keep only a unique copy of each object (hence
 * its name). For this, the method {@link #addOrGet} is the key API, which
 * allows retrieving existing values. This is quite similar to {@link THashSet},
 * but it allows accessing the existing value.
 * 
 * @author Erich Schubert
 *
 * @param <E>
 *            Value type
 */
public class Unique<E> extends THashSet<E> {
	/**
	 * Serial version number.
	 */
	static final long serialVersionUID = 1L;

	/**
	 * Constructor with default size and load factors.
	 */
	public Unique() {
		super();
	}

	/**
	 * Constructor with desired initial size.
	 *
	 * @param initialCapacity
	 *            desired initial size.
	 */
	public Unique(int initialCapacity) {
		super(initialCapacity);
	}

	/**
	 * Constructor with desired initial size, and with the specified load
	 * factor.
	 *
	 * @param initialCapacity
	 *            desired initial size
	 * @param loadFactor
	 *            load factor
	 */
	public Unique(int initialCapacity, float loadFactor) {
		super(initialCapacity, loadFactor);
	}

	/**
	 * Inserts a value into the set, unless it is already present.
	 * 
	 * This function returns the existing value, if present.
	 *
	 * @param obj
	 *            Object to insert or retrieve
	 * @return Existing object if already present, or the new object.
	 */
	public E addOrGet(E obj) {
		int index = insertKey(obj);

		if (index < 0) {
			@SuppressWarnings("unchecked")
			E ret = (E) _set[-index - 1];
			obj = ret;
		}

		postInsertHook(consumeFreeSlot);
		return obj;
	}
}
