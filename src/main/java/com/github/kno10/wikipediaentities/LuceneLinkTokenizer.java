package com.github.kno10.wikipediaentities;

import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.procedure.TObjectIntProcedure;

import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.ClassicFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.wikipedia.WikipediaTokenizer;
import org.apache.lucene.util.Version;

/**
 * Tokenize link texts seen in Wikipedia, to build a list of common link titles.
 * Count how often each target occurs.
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

	/** Link text -> map(target -> count) */
	Map<String, TObjectIntHashMap<String>> links = new HashMap<>();

	static final int MINSUPP = 5;

	/** Filter to only retain links with minimum support */
	private CounterFilter filter = new CounterFilter();

	/**
	 * Constructor
	 * 
	 * @param out
	 *            Output file name
	 */
	public LuceneLinkTokenizer(String out) {
		tokenizer = new WikipediaTokenizer(null);
		stream = tokenizer;
		// stream = new PorterStemFilter(tokenizer);
		stream = new ClassicFilter(stream);
		stream = new LowerCaseFilter(Version.LUCENE_36, stream);
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
			TObjectIntHashMap<String> seen = links.get(label);
			if (seen == null) {
				seen = new TObjectIntHashMap<>();
				links.put(label, seen);
			}
			seen.adjustOrPutValue(target, 1, 1);
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
		// We sort everything here. This is expensive, but makes the output
		// files nicer to use in the future.
		ArrayList<String> keys = new ArrayList<>(links.keySet());
		ArrayList<String> vals = new ArrayList<>();
		Collections.sort(keys);
		for (String key : keys) {
			filter.max = 0;
			TObjectIntHashMap<String> counter = links.get(key);
			counter.retainEntries(filter);
			if (counter.size() == 0)
				continue;
			writer.append(key);
			vals.addAll(counter.keySet());
			Collections.sort(vals);
			for (String val : vals) {
				int c = counter.get(val);
				if (c * 10 < filter.max)
					continue;
				writer.append('\t').append(val);
				writer.append(':').append(Integer.toString(c));
			}
			writer.append('\n');
			vals.clear(); // Will be reused
		}
		if (writer != System.out)
			writer.close();
	}

	public static final class CounterFilter implements TObjectIntProcedure<Object> {
		int max = 0;

		@Override
		public boolean execute(Object target, int count) {
			max = (count > max) ? count : max;
			return count > MINSUPP;
		}
	}
}
