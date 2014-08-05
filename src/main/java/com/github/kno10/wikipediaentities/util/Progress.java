package com.github.kno10.wikipediaentities.util;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Class for progress logging.
 * 
 * Currently, we print to {@code System.err}; while we should be using proper
 * logging.
 * 
 * @author Erich Schubert
 */
public class Progress {
	/** Step size */
	int step = 10;

	/** Name of this progress */
	String label;

	/** Current value */
	AtomicInteger counter;

	/** Maximum value */
	int max;

	/** Starting time of job */
	long start, prev;

	/**
	 * Constructor.
	 * 
	 * @param label
	 *            Name of this progress logger.
	 */
	public Progress(String label) {
		this.label = label;
		this.counter = new AtomicInteger();
		this.start = this.prev = System.currentTimeMillis();
	}

	/** Thread-safe increment */
	public void incrementAndLog() {
		final int c = counter.incrementAndGet();
		if (c % step == 0) {
			report(c);
			if (c == 20 * step)
				step *= 10;
		}
	}

	/**
	 * Report the current progress
	 * 
	 * @param c
	 *            Value to report.
	 */
	void report(final int c) {
		long now = System.currentTimeMillis();
		if (max > 0)
			System.err.format(
					"%s: %.2f%% %d (%.2f/s average, %.2f/s current).\n", label,
					c * 100. / max, c, (c * 1000. / (now - start)),
					(step * 1000. / (now - prev)));
		else
			System.err.format("%s: %d (%.2f/s average, %.2f/s current).\n",
					label, c, (c * 1000. / (now - start)),
					(step * 1000. / (now - prev)));
		prev = now;
	}

	/**
	 * Get the counter value.
	 * 
	 * @return Counter value
	 */
	public int get() {
		return counter.get();
	}

	/**
	 * Set the new maximum
	 * 
	 * @param max
	 *            Maximum value
	 */
	public void setMax(int max) {
		this.max = max;
	}

	/**
	 * Report a last time, at the end.
	 */
	public void end() {
		report(counter.get());
	}
}
