package com.github.kno10.wikipediaentities;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.ClassicFilter;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.wikipedia.WikipediaTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

/**
 * Class to load Wikipedia articles into a Lucene index.
 * 
 * @author Erich Schubert
 */
public class LuceneWikipediaIndexer extends AbstractHandler {
	/** Lucene field name for text */
	public static final String LUCENE_FIELD_TEXT = "t";

	/** Lucene field name for title */
	public static final String LUCENE_FIELD_TITLE = "c";

	/** Lucene field name for the links */
	public static final String LUCENE_FIELD_LINKS = "l";

	/** Lucene Wikipedia tokenizer */
	private WikipediaTokenizer tokenizer;

	/** Filtered token stream */
	private TokenStream stream;

	/** Patterns to strip from the wiki text */
	private Matcher stripBasics = Pattern.compile(
			"(<!--.*?-->|<math>(.*?)</math>|</?su[bp]>|^\\s*__\\w+__\\s*$)",
			Pattern.CASE_INSENSITIVE | Pattern.MULTILINE).matcher("");

	/** Pattern to strip all templates, as we cannot reasonably parse them */
	private Matcher stripTemplates = Pattern.compile("\\{\\{([^}{]*?)\\}\\}")
			.matcher("");

	/** Match links, which are not nested. */
	private Matcher linkMatcher = Pattern
			.compile(
					"\\[\\[\\s*([^\\]\\[\\|]*?)(?:\\s*#.*?)?(?:\\s*\\|\\s*([^\\]\\[\\#]*))?\\s*\\]\\]")
			.matcher("");

	/** More cruft to remove */
	private Matcher stripCruft = Pattern
			.compile(
					"(?:<ref(?:[^<]*</ref|\\s+name\\s*=\\s*[^<]*|[^<]*/>)>|\\{\\|(.*?)\\|\\}|^ *\\*+|\\[\\[(?:([^\\]\\[]*)\\s*\\|\\s*)?([^\\]\\[]*)\\]\\])",
					Pattern.CASE_INSENSITIVE).matcher("");

	/** Lucene index writer */
	private IndexWriter index;

	/** Handler to send link detected events to. */
	Handler handler;

	/**
	 * Constructor
	 * 
	 * @param dir
	 *            Directory for Lucene index.
	 * @param handler
	 *            Handlers for detected links.
	 * @throws IOException
	 *             on errors opening the lucene index
	 */
	public LuceneWikipediaIndexer(String dir, Handler handler)
			throws IOException {
		Set<String> skip = new HashSet<>();
		skip.add(WikipediaTokenizer.EXTERNAL_LINK_URL);
		tokenizer = new WikipediaTokenizer(null,
				WikipediaTokenizer.TOKENS_ONLY, skip);
		stream = tokenizer;
		stream = new ClassicFilter(stream); // Removes 's etc
		stream = new LowerCaseFilter(Version.LUCENE_36, stream);
		stream.addAttribute(CharTermAttribute.class);

		FSDirectory ldir = FSDirectory.open(new File(dir));
		IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_36,
				new StandardAnalyzer(Version.LUCENE_36));
		index = new IndexWriter(ldir, config);
		this.handler = handler;
	}

	StringBuilder buf = new StringBuilder();

	@Override
	public void rawArticle(String title, String intext) {
		CharSequence text = intext;
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
		{ // Parse, and replace links with their text only:
			buf.delete(0, buf.length()); // clear
			int pos = 0;
			linkMatcher.reset(text);
			while (linkMatcher.find()) {
				buf.append(text, pos, linkMatcher.start());
				String targ = linkMatcher.group(1);
				if (targ == null || targ.length() == 0) {
					buf.append(linkMatcher.group(2));
					pos = linkMatcher.end();
					continue; // Internal link.
				}
				targ = Util.normalizeLink(targ);
				if (targ == null) {
					System.err.println(linkMatcher.group(0));
					continue;
				}
				if (targ.charAt(0) == ':' || targ.startsWith("File:")
						|| targ.startsWith("Wikisource:")
						|| targ.startsWith("Wikipedia:")
						|| targ.startsWith("Commons:")
						|| targ.startsWith("Image:"))
					continue;
				String labl = linkMatcher.group(2);
				if (labl == null)
					labl = targ;
				labl = labl.replace('\n', ' ').trim();
				if (targ != null && addLink(targ, labl))
					handler.linkDetected(title, labl != null ? labl : targ,
							targ);

				buf.append(labl);
				pos = linkMatcher.end();
			}
			buf.append(text, pos, text.length());
			text = buf;
		}
		stripCruft.reset(text);
		text = stripCruft.replaceAll(""); // Converts to string!

		try {
			Document doc = new Document();
			doc.add(new Field(LUCENE_FIELD_TITLE, title, Field.Store.YES,
					Field.Index.NOT_ANALYZED_NO_NORMS));
			doc.add(new Field(LUCENE_FIELD_LINKS, serializeLinks(),
					Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS,
					Field.TermVector.NO));

			tokenizer.reset(new FastStringReader(text.toString()));
			stream.reset();
			doc.add(new Field(LUCENE_FIELD_TEXT, stream));
			index.addDocument(doc);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		clearLinks();
		handler.rawArticle(title, intext);
	}

	static class FastStringReader extends Reader {
		String text;
		int p, r;

		public FastStringReader(String text) {
			this.text = text;
			this.p = 0;
			this.r = text.length();
		}

		@Override
		public int read(char[] cbuf, int off, int len) throws IOException {
			int c = len < r ? len : r; // Number of characters to copy
			if (c == 0)
				return -1;
			text.getChars(p, p + c, cbuf, off);
			p += c; // Position
			r -= c; // Remaining
			return c;
		}

		@Override
		public void close() throws IOException {
			// ignore.
		}
	}

	ArrayList<String> links = new ArrayList<>();

	boolean addLink(String title, String label) {
		// There won't be that many duplicates for a hash map to pay off
		for (int i = 0, l = links.size(); i < l; i += 2) {
			if (links.get(i).equals(title) && links.get(i + 1).equals(label)) {
				return false; // Already in the document
			}
		}
		links.add(title);
		links.add(label);
		return true;
	}

	String serializeLinks() {
		StringBuilder buf = new StringBuilder();
		for (String s : links) {
			if (buf.length() > 0)
				buf.append('\t');
			buf.append(s);
		}
		return buf.toString();
	}

	void clearLinks() {
		links.clear();
	}

	@Override
	public void close() {
		handler.close();
		System.err.format("Closing %s output.", getClass().getSimpleName());
		try {
			index.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
}
