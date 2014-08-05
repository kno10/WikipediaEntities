package com.github.kno10.wikipediaentities;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.github.kno10.wikipediaentities.util.Util;

/**
 * Collect all redirections into an output file.
 * 
 * @author Erich Schubert
 */
public class RedirectCollector {
	/** Output filename */
	private String out;

	/** Hashmap storage */
	protected ConcurrentHashMap<String, String> redirects = new ConcurrentHashMap<>(
			100000);

	/** Has the transitive closure been computed */
	protected boolean closed = false;

	/**
	 * Constructor.
	 * 
	 * @param out
	 *            Output file name
	 */
	public RedirectCollector(String out) {
		this.out = out;
	}

	/** Compute the transitive closure of redirects. */
	public synchronized Map<String, String> transitiveClosure() {
		if (closed)
			return redirects;
		System.err.println("Computing transitive closure of redirects.");
		HashSet<String> seen = new HashSet<>();
		// Iterate using a copy to avoid concurrent modification
		for (String key : new ArrayList<>(redirects.keySet())) {
			String targ = redirects.get(key), targ2;
			assert (targ != null);
			seen.add(key);
			seen.add(targ);
			while ((targ2 = redirects.get(targ)) != null) {
				if (!seen.add(targ2)) {
					System.err.format(
							"Redirect cycle detected involving %s > %s > %s\n",
							key, targ, targ2);
					break;
				}
				targ = targ2;
			}
			if (key.equals(targ))
				redirects.remove(key);
			else
				redirects.put(key, targ);
			seen.clear();
		}
		closed = true;
		return redirects;
	}

	public void close() throws IOException {
		transitiveClosure();
		System.err.format("Closing %s output.\n", getClass().getSimpleName());
		PrintStream writer = Util.openOutput(out);
		for (String title : redirects.keySet())
			writer.append(title).append('\t').append(redirects.get(title))
					.append('\n');
		if (writer != System.out)
			writer.close();
	}

	public Handler makeThreadHandler() {
		return new RedirectHandler();
	}

	private class RedirectHandler extends AbstractHandler {
		@Override
		public void redirect(String title, String redirect, String anchor) {
			if (redirect == null || redirect.length() == 0)
				return;
			if (anchor != null)
				redirects.put(title, redirect + '\t' + anchor);
			else
				redirects.put(title, redirect);
		}
	}
}
