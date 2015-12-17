package com.github.kno10.wikipediaentities.util;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

/**
 * This hash set is designed to keep only a unique copy of each object (hence
 * its name). For this, the method {@link #addOrGet} is the key API, which
 * allows retrieving existing values. This is quite similar to {@link THashSet},
 * but it allows accessing the existing value.
 *
 * @author Erich Schubert
 *
 * @param <E> Value type
 */
public class Unique<E> extends ObjectOpenHashSet<E> {
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
   * @param initialCapacity desired initial size.
   */
  public Unique(int initialCapacity) {
    super(initialCapacity);
  }

  /**
   * Constructor with desired initial size, and with the specified load factor.
   *
   * @param initialCapacity desired initial size
   * @param loadFactor load factor
   */
  public Unique(int initialCapacity, float loadFactor) {
    super(initialCapacity, loadFactor);
  }

  /**
   * Inserts a value into the set, unless it is already present.
   *
   * This function returns the existing value, if present. k
   *
   * @param obj Object to insert or retrieve
   * @return Existing object if already present, or the new object.
   */
  public E addOrGet(E k) {
    if(k == null) {
      return null;
    }
    // Careful, this is VERSION-DEPENDANT on fastutil, unfortunately.
    int pos;
    E curr;
    final E[] key = this.key;
    if(!((curr = key[pos = (HashCommon.mix((k).hashCode())) & mask]) == null)) {
      if(curr.equals(k)) {
        return curr;
      }
      while(!((curr = key[pos = (pos + 1) & mask]) == null)) {
        if(curr.equals(k)) {
          return curr;
        }
      }
    }
    key[pos] = k;
    if(size++ >= maxFill) {
      rehash(HashCommon.arraySize(size + 1, f));
    }
    return k;
  }
}
