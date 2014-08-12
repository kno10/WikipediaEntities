package com.github.kno10.wikipediaentities;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;

import com.github.kno10.wikipediaentities.util.CounterSet;
import com.github.kno10.wikipediaentities.util.Progress;
import com.github.kno10.wikipediaentities.util.Unique;
import com.github.kno10.wikipediaentities.util.Util;

public class AnalyzeLinks {
	private static final int MINIMUM_MENTIONS = 20;

	/** Collect unique strings. */
	Unique<String> unique = new Unique<>();

	Progress prog = new Progress("Computing support");

	BlockingQueue<Candidate> proqueue = new ArrayBlockingQueue<>(1000);

	BlockingQueue<Candidate> outqueue = new ArrayBlockingQueue<>(1000 + 1);

	boolean shutdown = false;

	/** Lucene index searcher */
	IndexSearcher searcher;

	private void run() throws IOException {
		int par = Math.min(Integer.valueOf(Config.get("parallelism")), Runtime
				.getRuntime().availableProcessors());
		if (par < 1) {
			throw new Error("At least 1 consumer must be allowed!");
		}

		String nam = Config.get("linktext.output");
		String dir = Config.get("indexer.dir");
		String out = Config.get("entities.output");
		FSDirectory ldir = FSDirectory.open(new File(dir));
		IndexReader reader = IndexReader.open(ldir);
		searcher = new IndexSearcher(reader);

		ArrayList<Thread> threads = new ArrayList<>();
		threads.add(new OutputThread(out));
		for (int i = 0; i < par; i++)
			threads.add(new WorkerThread("Worker-" + i));

		// Start all:
		for (Thread th : threads)
			th.start();
		readall(nam);
		// Wait for all:
		for (Thread th : threads)
			try {
				th.join();
				// Help the writer thread to shutdown...
				synchronized (monitor) {
					monitor.notify();
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

		searcher.close();
	}

	/**
	 * Class wrapping a candidate.
	 * 
	 * If the candidate has "failed", we {@code null} the query string to
	 * indicate this to the output thread.
	 */
	static class Candidate {
		String query, matches;

		public Candidate(String query) {
			this.query = query;
			this.matches = null;
		}

		@Override
		public String toString() {
			return query + " " + matches;
		}
	}

	/** Used for awaking the output thread */
	Object monitor = new Object();

	private class WorkerThread extends Thread {
		CounterSet<String> counters = new CounterSet<>();
		HashSet<String> cands = new HashSet<>();
		StringBuilder buf = new StringBuilder();

		public WorkerThread(String name) {
			super(name);
		}

		@Override
		public void run() {
			while (!proqueue.isEmpty() || !shutdown) {
				try {
					Candidate a = proqueue.poll(100, TimeUnit.MILLISECONDS);
					if (a == null)
						continue;
					analyze(a);
					prog.incrementAndLog();
				} catch (InterruptedException e) {
					break;
				} catch (IOException e) {
					e.printStackTrace();
					break;
				}
			}
		}

		private void analyze(Candidate cand) throws IOException {
			String[] s = cand.query.split("\t");
			PhraseQuery pq = new PhraseQuery();
			for (String t : s[0].split(" "))
				pq.add(new Term(LuceneWikipediaIndexer.LUCENE_FIELD_TEXT, t));
			// s[1] = number of links with this string
			if (Integer.valueOf(s[1]) < MINIMUM_MENTIONS) {
				cand.query = null; // Flag as dead.
				return;
			}
			cands.clear();
			counters.clear();
			for (int j = 2; j < s.length; ++j) {
				final int postfix = s[j].lastIndexOf(':');
				final int v = Integer.parseInt(s[j].substring(postfix + 1));
				String key = s[j].substring(0, postfix);
				counters.adjustOrPutValue(key, v, v);
				cands.add(key);
			}
			TopDocs res = searcher.search(pq, 10000);
			ScoreDoc[] docs = res.scoreDocs;
			if (docs.length < MINIMUM_MENTIONS) {
				cand.query = null; // Flag as dead.
				return; // Too rare.
			}
			int minsupp = Math.max(MINIMUM_MENTIONS, docs.length / 10);
			for (int i = 0; i < docs.length; ++i) {
				Document d = searcher.doc(docs[i].doc);
				// String dtitle = d
				// .get(LuceneWikipediaIndexer.LUCENE_FIELD_TITLE);
				String[] lis = d.get(LuceneWikipediaIndexer.LUCENE_FIELD_LINKS)
						.split("\t");
				if (lis.length == 0)
					// System.err.format("No links for %s.\n", dtitle);
					continue;
				// Odd positions are link targets:
				for (int j = 1; j < lis.length; j += 2)
					counters.count(lis[j]);
			}
			buf.delete(0, buf.length()); // clear
			buf.append(s[0]);
			buf.append('\t').append(res.totalHits);
			boolean output = false;
			for (CounterSet.Entry<String> c : counters.descending()) {
				final int count = c.getCount();
				if (count < minsupp)
					break;
				if (count / 4 > minsupp) // Increase cutoff
					minsupp = count / 4;
				if (!cands.contains(c.getKey()))
					continue; // Was not a candidate.
				int conf = (int) (count * 100. / res.totalHits);
				buf.append('\t').append(c.getKey());
				buf.append(':').append(count);
				buf.append(':').append(conf).append('%');
				output = true;
			}
			if (output)
				cand.matches = buf.toString(); // Flag as good.
			else
				cand.query = null; // Flag as dead.
			// Wake up writer thread, if waiting.
			synchronized (monitor) {
				monitor.notifyAll();
			}
		}
	}

	public void readall(String nam) {
		try (InputStream in = Util.openInput(nam);
				BufferedReader r = new BufferedReader(new InputStreamReader(in))) {
			String line;
			while ((line = r.readLine()) != null) {
				try {
					Candidate cand = new Candidate(line);
					proqueue.put(cand);
					outqueue.put(cand);
				} catch (InterruptedException e) {
					break;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		shutdown = true; // Don't wait for more input to arrive.
	}

	private class OutputThread extends Thread {
		private String nam;

		public OutputThread(String nam) {
			super("Output Thread");
			this.nam = nam;
		}

		@Override
		public void run() {
			try (PrintStream out = Util.openOutput(nam)) {
				while (!outqueue.isEmpty() || !shutdown) {
					try {
						Candidate a = outqueue.poll(100, TimeUnit.MILLISECONDS);
						if (a == null)
							continue;
						while (true) {
							if (a.query == null)
								break; // Query failed to yield good results.
							if (a.matches != null) { // Success
								out.append(a.matches);
								out.append('\n');
								break;
							}
							synchronized (monitor) {
								monitor.wait(); // Wait for wakeup signal
							}
						}
					} catch (InterruptedException e) {
						break;
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			System.err.println("Output thread has finished!");
		}
	}

	public static void main(String[] args) {
		try {
			(new AnalyzeLinks()).run();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
