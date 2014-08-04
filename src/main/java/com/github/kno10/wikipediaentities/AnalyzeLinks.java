package com.github.kno10.wikipediaentities;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.FSDirectory;

public class AnalyzeLinks {
	private static final int MINIMUM_MENTIONS = 25;

	/** Collect unique strings. */
	Unique<String> unique = new Unique<>();

	private void run() throws IOException {
		Map<String, String> redirects = loadRedirects(Config
				.get("redirects.output"));
		System.err.format("Read %d redirects.\n", redirects.size());
		String nam = Config.get("linktext.output");
		String dir = Config.get("indexer.dir");
		FSDirectory ldir = FSDirectory.open(new File(dir));
		IndexReader reader = IndexReader.open(ldir);
		IndexSearcher searcher = new IndexSearcher(reader);

		CounterSet<String> counters = new CounterSet<>();
		HashSet<String> cands = new HashSet<>();
		StringBuilder buf = new StringBuilder();

		try (InputStream in = Util.openInput(nam);
				BufferedReader r = new BufferedReader(new InputStreamReader(in))) {
			String line;
			while ((line = r.readLine()) != null) {
				String[] s = line.split("\t");
				PhraseQuery pq = new PhraseQuery();
				for (String t : s[0].split(" "))
					pq.add(new Term(LuceneWikipediaIndexer.LUCENE_FIELD_TEXT, t));
				// s[1] = number of links with this string
				if (Integer.valueOf(s[1]) < MINIMUM_MENTIONS)
					continue;
				cands.clear();
				counters.clear();
				for (int j = 2; j < s.length; ++j) {
					final int postfix = s[j].lastIndexOf(':');
					final int v = Integer.parseInt(s[j].substring(postfix + 1));
					String key = s[j].substring(0, postfix);
					counters.adjustOrPutValue(key, v, v);
					cands.add(key);
				}
				ScoreDoc[] docs = searcher.search(pq, 10000).scoreDocs;
				if (docs.length < MINIMUM_MENTIONS)
					continue; // Too rare.
				int minsupp = Math.max(MINIMUM_MENTIONS, docs.length / 10);
				for (int i = 0; i < docs.length; ++i) {
					Document d = searcher.doc(docs[i].doc);
					// String dtitle = d
					// .get(LuceneWikipediaIndexer.LUCENE_FIELD_TITLE);
					String[] lis = d.get(
							LuceneWikipediaIndexer.LUCENE_FIELD_LINKS).split(
							"\t");
					if (lis.length == 0)
						// System.err.format("No links for %s.\n", dtitle);
						continue;
					// Odd positions are link targets:
					for (int j = 1; j < lis.length; j += 2)
						counters.count(lis[j]);
				}
				buf.delete(0, buf.length()); // clear
				buf.append(s[0]);
				buf.append('\t').append(docs.length);
				boolean output = false;
				for (CounterSet.Entry<String> c : counters.descending()) {
					final int count = c.getCount();
					if (count < minsupp)
						break;
					if (count / 4 > minsupp) // Increase cutoff
						minsupp = count / 4;
					if (!cands.contains(c.getKey()))
						continue; // Was not a candidate.
					int conf = (int)(count * 100. / docs.length);
					buf.append('\t').append(c.getKey());
					buf.append(':').append(count);
					buf.append(':').append(conf).append('%');
					output = true;
				}
				if (output)
					System.out.println(buf);
			}
		}
		searcher.close();
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

	public static void main(String[] args) {
		try {
			(new AnalyzeLinks()).run();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
