package com.github.kno10.wikipediaentities.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

/**
 * Count Objects.
 *
 * @author Erich Schubert
 */
public class CounterSet {
  /**
   * Get a descending list of counted items.
   *
   * @return List of items.
   */
  public static <O> List<Entry<O>> descending(Object2IntOpenHashMap<O> counters) {
    ArrayList<Entry<O>> copy = new ArrayList<>(counters.size());
    for(Iterator<Object2IntMap.Entry<O>> iter = counters.object2IntEntrySet().fastIterator(); iter.hasNext();) {
      // Note: fast iterator will recycle this object!
      Object2IntMap.Entry<O> entry = iter.next();
      copy.add(new Entry<O>(entry.getKey(), entry.getIntValue()));
    }
    Collections.sort(copy);
    return copy;
  }

  /**
   * Copy of the data used for sorted iteration.
   *
   * @author Erich Schubert
   *
   * @param <O> Key type
   */
  public static class Entry<O> implements Comparable<Entry<O>> {
    /** Data key */
    private O key;

    /** Data value */
    private int count;

    /**
     * Constructor.
     *
     * @param key Key
     * @param count Count
     */
    private Entry(O key, int count) {
      super();
      this.key = key;
      this.count = count;
    }

    public O getKey() {
      return key;
    }

    public int getCount() {
      return count;
    }

    @Override
    public int compareTo(Entry<O> o) {
      final int c1 = count, c2 = o.count;
      return c1 > c2 ? -1 : c1 < c2 ? +1 : 0;
    }
  }

  /**
   * Filter to prune rare items, find the max and count the total sum.
   *
   * @author Erich Schubert
   */
  public static final class CounterFilter {
    /** Minimum threshold */
    private final int min;

    /** Statistics */
    private int max = 0, sum = 0;

    /**
     * Constructor.
     *
     * @param min Minimum value
     */
    public CounterFilter(int min) {
      super();
      this.min = min;
    }

    public boolean execute(Object target, int count) {
      max = (count > max) ? count : max;
      sum += count;
      return count >= min;
    }

    /** Reset statistics. */
    public void reset() {
      max = 0;
      sum = 0;
    }

    /** Maximum value */
    public int getMax() {
      return max;
    }

    /** Sum of values */
    public int getSum() {
      return sum;
    }
  }

  /**
   * Merge second counter set.
   *
   * @param other Other set of counters.
   */
  public static <O> void update(Object2IntOpenHashMap<O> first, Object2IntOpenHashMap<O> second) {
    for(Iterator<Object2IntMap.Entry<O>> iter = second.object2IntEntrySet().fastIterator(); iter.hasNext();) {
      Object2IntMap.Entry<O> entry = iter.next();
      second.addTo(entry.getKey(), entry.getIntValue());
    }
  }
}
