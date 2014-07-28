package com.github.kno10.wikipediaentities;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Collect all redirections into an output file.
 * 
 * @author Erich Schubert
 */
public class RedirectCollector extends AbstractHandler {
	/** Output filename */
	private String out;

	/** Hashmap storage */
	protected Map<String, String> redirects = new HashMap<>();

	/**
	 * Constructor.
	 * 
	 * @param out
	 *            Output file name
	 */
	public RedirectCollector(String out) {
		this.out = out;
	}

	@Override
	public void redirect(String title, String redirect) {
		redirect = Util.normalizeLink(redirect, null);
		if (redirect == null || redirect.length() == 0)
			return;
		redirects.put(title, redirect);
	}

	/** Compute the transitive closure of redirects. */
	private void transitiveClosure() {
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
			redirects.put(key, targ);
			seen.clear();
		}
	}

	@Override
	public void close() {
		transitiveClosure();
		System.err.format("Closing %s output.\n", getClass().getSimpleName());
		try (PrintStream writer = Util.openOutput(out)) {
			for (String title : redirects.keySet())
				writer.append(title).append('\t').append(redirects.get(title))
						.append('\n');
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
}
