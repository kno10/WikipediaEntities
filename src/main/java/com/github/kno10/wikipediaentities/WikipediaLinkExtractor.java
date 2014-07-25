package com.github.kno10.wikipediaentities;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extract internal links from a Wikipedia article.
 * 
 * Not every link is extracted: we deliberately ignore math mode and references.
 * 
 * @author Erich Schubert
 */
public class WikipediaLinkExtractor extends AbstractHandler {
	/** Patterns to strip from the wiki text */
	private Matcher stripBasics = Pattern
			.compile(
					"(<!--.*?-->|<math>(.*?)</math>|</?su[bp]>|'{2,}| *={2,} *|__(NO)TOC__)",
					Pattern.CASE_INSENSITIVE).matcher("");

	/** Pattern to strip all templates, as we cannot reasonably parse them */
	private Matcher stripTemplates = Pattern.compile("\\{\\{([^}{]*?)\\}\\}")
			.matcher("");

	/** Pattern to remove references */
	private Matcher stripRefs = Pattern.compile(
			"<ref([^<]*</ref|\\s+name\\s*=\\s*[^<]*)>",
			Pattern.CASE_INSENSITIVE).matcher("");

	/** Match links, which are not nested. */
	private Matcher linkMatcher = Pattern
			.compile(
					"\\[\\[\\s*(?:([^\\]\\[\\|:]*)\\s*\\|\\s*)?([^\\]\\[\\|:]*)\\s*\\]\\]")
			.matcher("");

	Handler handler;

	/**
	 * Constructor.
	 * 
	 * @param handler
	 *            Event handler
	 */
	public WikipediaLinkExtractor(Handler handler) {
		super();
		this.handler = handler;
	}

	@Override
	public void rawArticle(String title, String text) {
		// System.err.print(title + ": ");
		stripBasics.reset(text);
		text = stripBasics.replaceAll("");
		for (int i = 0; i < 4; i++) {
			stripTemplates.reset(text);
			String text2 = stripTemplates.replaceAll("");
			if (text2.equals(text))
				break; // No more changes
			text = text2;
		}
		stripRefs.reset(text);
		text = stripRefs.replaceAll("");
		// Parse, and replace links with their text.
		linkMatcher.reset(text);
		while (linkMatcher.find()) {
			String targ = linkMatcher.group(1);
			String labl = linkMatcher.group(2).replace('\n', ' ');
			if (targ == null || targ.length() == 0)
				targ = labl;
			targ = targ.replace('\n', ' ');
			targ = Util.normalizeLink(targ, labl);
			if (targ == null)
				continue;
			handler.linkDetected(title, labl, targ);
		}
	}

	@Override
	public void close() {
		System.err.format("Closing %s child handler.", getClass()
				.getSimpleName());
		handler.close();
	}
}
