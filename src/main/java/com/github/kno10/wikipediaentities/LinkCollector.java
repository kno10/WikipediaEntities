package com.github.kno10.wikipediaentities;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Collect all outgoing internal links from each Wikipedia article.
 * 
 * @author Erich Schubert
 */
public class LinkCollector extends AbstractHandler {
	/** Current page name. */
	String cur = null;

	/** Observed link targets in current page */
	Set<String> targets = new HashSet<>();

	/** Output writer */
	PrintStream writer;

	/**
	 * Constructor
	 * 
	 * @param out
	 *            Output stream
	 * @throws IOException
	 *             When output file cannot be created
	 */
	public LinkCollector(String out) throws IOException {
		writer = Util.openOutput(out);
	}

	@Override
	public void linkDetected(String title, String label, String target) {
		if (!title.equals(cur))
			nextEntry(title);
		targets.add(target);
	}

	/**
	 * Finish the current entry, proceed to the next.
	 * 
	 * @param next
	 *            Next entry name
	 */
	private void nextEntry(String next) {
		// Write and close previous entry
		if (cur != null) {
			writer.append(cur);
			List<String> t = new ArrayList<>(targets);
			Collections.sort(t);
			for (String s : t)
				writer.append('\t').append(s);
			writer.append('\n');
		}
		cur = next;
		targets.clear();
	}

	@Override
	public void close() {
		nextEntry(null);
		System.err.format("Closing %s output.\n", getClass().getSimpleName());
		if (writer != System.out)
			writer.close();
	}
}
