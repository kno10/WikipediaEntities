package com.github.kno10.wikipediaentities;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
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
	public static final String LUCENE_FIELD_TEXT = "text";

	/** Lucene field name for title */
	public static final String LUCENE_FIELD_TITLE = "title";

	/** Lucene Wikipedia tokenizer */
	private WikipediaTokenizer tokenizer;

	/** Filtered token stream */
	private TokenStream stream;

	/** Patterns to strip from the wiki text */
	private Matcher stripBasics = Pattern.compile(
			"(<!--.*?-->|<math>(.*?)</math>|</?su[bp]>|__(NO)TOC__)",
			Pattern.CASE_INSENSITIVE).matcher("");

	/** Pattern to strip all templates, as we cannot reasonably parse them */
	private Matcher stripTemplates = Pattern.compile("\\{\\{([^}{]*?)\\}\\}")
			.matcher("");

	/** Match links, which are not nested. */
	private Matcher linkMatcher = Pattern
			.compile(
					"\\[\\[\\s*([^\\]\\[\\|]*?)(?:#.*?)?(?:\\s*\\|\\s*([^\\]\\[\\#]*))?\\s*\\]\\]")
			.matcher("");

	/** More cruft to remove */
	private Matcher stripCruft = Pattern
			.compile(
					"(<ref([^<]*</ref|\\s+name\\s*=\\s*[^<]*)>|\\{\\|(.*?)\\|\\}|^ *\\*+|\\[\\[(?:([^\\]\\[]*)\\s*\\|\\s*)?([^\\]\\[]*)\\]\\])",
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
		stream = new LowerCaseFilter(Version.LUCENE_36, tokenizer);
		stream.addAttribute(CharTermAttribute.class);

		FSDirectory ldir = FSDirectory.open(new File(dir));
		IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_36,
				new StandardAnalyzer(Version.LUCENE_36));
		index = new IndexWriter(ldir, config);
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
		{ // Parse, and replace links with their text only:
			StringBuilder buf = new StringBuilder();
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
				String labl = linkMatcher.group(2);
				if (labl == null)
					labl = targ;
				labl = labl.replace('\n', ' ').trim();
				if (targ != null)
					handler.linkDetected(title, labl != null ? labl : targ,
							targ);

				buf.append(labl);
				pos = linkMatcher.end();
			}
			buf.append(text, pos, text.length());
			text = buf.toString();
		}
		stripCruft.reset(text);
		text = stripCruft.replaceAll("");

		try {
			Document doc = new Document();
			doc.add(new Field(LUCENE_FIELD_TITLE, title, Field.Store.YES,
					Field.Index.NOT_ANALYZED_NO_NORMS));

			tokenizer.reset(new StringReader(text));
			stream.reset();
			doc.add(new Field(LUCENE_FIELD_TEXT, stream));
			index.addDocument(doc);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
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
