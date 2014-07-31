package com.github.kno10.wikipediaentities;

import gnu.trove.iterator.TObjectIntIterator;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.procedure.TObjectIntProcedure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Count Objects.
 * 
 * @author Erich Schubert
 */
public class CounterSet<O> extends TObjectIntHashMap<O> {
	/**
	 * Count an object occurence.
	 * 
	 * @param obj
	 *            Object
	 */
	public void count(O obj) {
		adjustOrPutValue(obj, 1, 1);
	}

	/**
	 * Get a descending list of counted items.
	 * 
	 * @return List of items.
	 */
	public List<Entry<O>> descending() {
		ArrayList<Entry<O>> copy = new ArrayList<>();
		for (TObjectIntIterator<O> it = this.iterator(); it.hasNext();) {
			it.advance();
			copy.add(new Entry<O>(it.key(), it.value()));
		}
		Collections.sort(copy);
		return copy;
	}

	/**
	 * Copy of the data used for sorted iteration.
	 * 
	 * @author Erich Schubert
	 *
	 * @param <O>
	 *            Key type
	 */
	public static class Entry<O> implements Comparable<Entry<O>> {
		/** Data key */
		private O key;
		/** Data value */
		private int count;

		/**
		 * Constructor.
		 * 
		 * @param key
		 *            Key
		 * @param count
		 *            Count
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
	public static final class CounterFilter implements
			TObjectIntProcedure<Object> {
		/** Minimum threshold */
		private final int min;

		/** Statistics */
		private int max = 0, sum = 0;

		/**
		 * Constructor.
		 * 
		 * @param min
		 *            Minimum value
		 */
		public CounterFilter(int min) {
			super();
			this.min = min;
		}

		@Override
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
	 * Class to sort by count.
	 * 
	 * @author Erich Schubert
	 */
	public static final class Sorter implements Comparator<Object> {
		TObjectIntHashMap<?> data;

		/**
		 * Constructor
		 * 
		 * @param data
		 *            Sorting keys
		 */
		public Sorter(TObjectIntHashMap<?> data) {
			super();
			this.data = data;
		}

		@Override
		public int compare(Object o1, Object o2) {
			int c1 = data.get(o1), c2 = data.get(o2);
			return c1 > c2 ? -1 : c1 < c2 ? +1 : 0;
		}

		/**
		 * Assign a different counting storage.
		 * 
		 * @param data
		 *            Storage
		 */
		public void setCounts(TObjectIntHashMap<?> data) {
			this.data = data;
		}
	}

}
