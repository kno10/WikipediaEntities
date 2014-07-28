package com.github.kno10.wikipediaentities;

import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.wikipedia.WikipediaTokenizer;
import org.apache.lucene.util.Version;

/**
 * Tokenize link texts seen in Wikipedia, to build a list of common link titles.
 * 
 * @author Erich Schubert
 */
public class LuceneLinkTokenizer extends AbstractHandler {
	/** Lucene Wikipedia tokenizer */
	WikipediaTokenizer tokenizer;

	/** Filtered token stream */
	TokenStream stream;

	/** Lucene character term attribute */
	CharTermAttribute termAtt;

	/** Output file name */
	private String out;

	/** Link index */
	Map<String, Set<String>> links = new HashMap<>();

	/**
	 * Constructor
	 * 
	 * @param out
	 *            Output file name
	 */
	public LuceneLinkTokenizer(String out) {
		tokenizer = new WikipediaTokenizer(null);
		// stream = new PorterStemFilter(tokenizer);
		stream = new LowerCaseFilter(Version.LUCENE_35, tokenizer);
		termAtt = stream.addAttribute(CharTermAttribute.class);
		this.out = out;
	}

	@Override
	public void linkDetected(String title, String label, String target) {
		// Normalize the link text.
		try {
			StringBuilder buf = null;
			tokenizer.reset(new StringReader(label));
			stream.reset();
			while (stream.incrementToken()) {
				if (termAtt.length() <= 0)
					continue;
				if (buf == null)
					buf = new StringBuilder();
				else
					buf.append(' ');
				buf.append(termAtt.buffer(), 0, termAtt.length());
			}
			if (buf == null)
				return;
			label = buf.toString();
			Set<String> seen = links.get(label);
			if (seen == null) {
				seen = new HashSet<>();
				links.put(label, seen);
			}
			seen.add(target);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void close() {
		System.err.format("Closing %s output.\n", getClass().getSimpleName());
		PrintStream writer = null;
		try {
			writer = Util.openOutput(out);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		ArrayList<String> keys = new ArrayList<>(links.keySet());
		ArrayList<String> vals = new ArrayList<>();
		Collections.sort(keys);
		for (String key : keys) {
			writer.append(key);
			vals.addAll(links.get(key));
			Collections.sort(vals);
			for (String val : vals)
				writer.append('\t').append(val);
			writer.append('\n');
			vals.clear(); // Will be reused
		}
		if (writer != System.out)
			writer.close();
	}
}
