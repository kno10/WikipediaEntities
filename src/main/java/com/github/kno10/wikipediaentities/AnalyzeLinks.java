package com.github.kno10.wikipediaentities;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.FSDirectory;

public class AnalyzeLinks {
	private static final int MINIMUM_MENTIONS = 100;

	/** Collect unique strings. */
	Unique<String> unique = new Unique<>();

	private void run() throws IOException {
		Map<String, String> redirects = loadRedirects(Config
				.get("redirects.output"));
		System.err.format("Read %d redirects.\n", redirects.size());
		Map<String, Collection<String>> links = loadLinks(
				Config.get("links.output"), redirects);
		System.err.format("Read %d links.\n", links.size());
		String nam = Config.get("linktext.output");

		String dir = Config.get("indexer.dir");
		FSDirectory ldir = FSDirectory.open(new File(dir));
		IndexReader reader = IndexReader.open(ldir);
		IndexSearcher searcher = new IndexSearcher(reader);

		HashMap<String, Counter> counters = new HashMap<>();

		try (InputStream in = Util.openInput(nam);
				BufferedReader r = new BufferedReader(new InputStreamReader(in))) {
			String line;
			while ((line = r.readLine()) != null) {
				String[] s = line.split("\t");
				PhraseQuery pq = new PhraseQuery();
				for (String t : s[0].split(" "))
					pq.add(new Term(LuceneWikipediaIndexer.LUCENE_FIELD_TEXT, t));
				ScoreDoc[] docs = searcher.search(pq, 10000).scoreDocs;
				if (docs.length < MINIMUM_MENTIONS)
					continue; // Too rare.
				int minsupp = Math.max(MINIMUM_MENTIONS, docs.length / 10);
				for (int i = 0; i < docs.length; ++i) {
					Document d = searcher.doc(docs[i].doc);
					String dtitle = d
							.get(LuceneWikipediaIndexer.LUCENE_FIELD_TITLE);
					Collection<String> lis = links.get(dtitle);
					if (lis == null) {
						// System.err.format("No links for %s.\n", dtitle);
						continue;
					}
					for (String l : lis) {
						Counter c = counters.get(l);
						if (c == null) {
							c = new Counter(l);
							counters.put(l, c);
						}
						c.count++;
					}
				}
				ArrayList<Counter> cs = new ArrayList<>(counters.values());
				Collections.sort(cs);
				System.out.append(s[0]);
				System.out.format("\t%d", docs.length);
				for (Counter c : cs) {
					if (c.count < minsupp)
						break;
					if (c.count / 4 > minsupp) // Increase cutoff
						minsupp = c.count / 4;
					System.out.format(Locale.ENGLISH, "\t%s:%d:%.3f", c.target,
							c.count, c.count / (double) docs.length);
				}
				System.out.println();
				counters.clear();
			}
		}
		searcher.close();
	}

	class Counter implements Comparable<Counter> {
		int count = 0;
		final String target;

		public Counter(String target) {
			this.target = target;
		}

		@Override
		public int compareTo(Counter o) {
			return count > o.count ? -1 : count < o.count ? +1 : 0;
		}
	}

	private Map<String, String> loadRedirects(String nam) throws IOException {
		HashMap<String, String> redirects = new HashMap<>();
		try (InputStream in = Util.openInput(nam);
				BufferedReader r = new BufferedReader(new InputStreamReader(in))) {
			String line;
			int c = 0;
			while ((line = r.readLine()) != null) {
				String[] s = line.split("\t");
				assert (s.length == 2);
				redirects.put(unique.addOrGet(s[0]), unique.addOrGet(s[1]));
				++c;
				if (c % 1000000 == 0)
					System.err.format("Loading redirects %d...\n", c);
			}
		}
		return redirects;
	}

	private Map<String, Collection<String>> loadLinks(String nam,
			Map<String, String> redirects) throws IOException {
		int c = 0;
		HashMap<String, Collection<String>> links = new HashMap<>();
		Set<String> l = new HashSet<>();
		try (InputStream in = Util.openInput(nam);
				BufferedReader r = new BufferedReader(new InputStreamReader(in))) {
			String line;
			while ((line = r.readLine()) != null) {
				String[] s = line.split("\t");
				for (int i = 1; i < s.length; i++) {
					String targ = redirects.get(s[i]);
					l.add(targ != null ? targ : unique.addOrGet(s[i]));
				}
				links.put(unique.addOrGet(s[0]), new ArrayList<>(l));
				l.clear();
				++c;
				if (c % 1000000 == 0)
					System.err.format("Loading links %d...\n", c);
			}
		}
		return links;
	}

	public static void main(String[] args) {
		try {
			(new AnalyzeLinks()).run();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
