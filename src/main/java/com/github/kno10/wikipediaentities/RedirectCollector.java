package com.github.kno10.wikipediaentities;

import java.io.IOException;
import java.io.PrintStream;

/**
 * Collect all redirections into an output file.
 * 
 * @author Erich Schubert
 */
public class RedirectCollector extends AbstractHandler {
	/** Output stream */
	private PrintStream writer;

	/**
	 * Constructor.
	 * 
	 * @param out
	 *            Output file name
	 * @throws IOException
	 *             When output file could not be created.
	 */
	public RedirectCollector(String out) throws IOException {
		writer = Util.openOutput(out);
	}

	@Override
	public void redirect(String title, String redirect) {
		redirect = Util.normalizeLink(redirect, null);
		if (redirect == null || redirect.length() == 0)
			return;
		// redirects.put(title, redirect);
		writer.append(title).append('\t').append(redirect).append('\n');
	}

	@Override
	public void close() {
		System.err.format("Closing %s output.\n", getClass().getSimpleName());
		if (writer != System.out)
			writer.close();
	}
}
