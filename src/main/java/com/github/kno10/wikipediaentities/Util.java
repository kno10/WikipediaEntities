package com.github.kno10.wikipediaentities;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Writer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.text.translate.EntityArrays;

import com.github.kno10.wikipediaentities.stringutil.PrefixTreeMatcher;

/**
 * Utility functions.
 * 
 * @author Erich Schubert
 */
public class Util {
	/**
	 * Open an output stream.
	 * 
	 * When the output file name is {@code null}, stdout will be used.
	 * 
	 * @param out
	 *            Output file name
	 * @return Output stream
	 * @throws IOException
	 */
	public static PrintStream openOutput(String out) throws IOException {
		if (out == null)
			return System.out;
		if (out.endsWith(".gz"))
			return new PrintStream(//
					new GZIPOutputStream(//
							new FileOutputStream(out)));
		return new PrintStream(new FileOutputStream(out));
	}

	protected static PrefixTreeMatcher PREFIXMATCHER = new PrefixTreeMatcher();

	// Build the prefix tree
	static {
		PREFIXMATCHER = PrefixTreeMatcher.makeNumericalEntityMatcher();
		for (String[] p : EntityArrays.BASIC_UNESCAPE())
			PREFIXMATCHER.add(p[0], p[1]);
		for (String[] p : EntityArrays.ISO8859_1_UNESCAPE())
			PREFIXMATCHER.add(p[0], p[1]);
		for (String[] p : EntityArrays.HTML40_EXTENDED_UNESCAPE())
			PREFIXMATCHER.add(p[0], p[1]);
		PREFIXMATCHER.add("–", "-");
		PREFIXMATCHER.add("—", "-");
		PREFIXMATCHER.add("`", "'");
		PREFIXMATCHER.add("’", "'");
		PREFIXMATCHER.add("\t", " ");
		PREFIXMATCHER.add("\r\n", "\n");
		PREFIXMATCHER.add("\r", "\n");
	}

	/**
	 * Fast replace all HTML entities, because Apache commons
	 * {@link StringEscapeUtils#unescapeHtml4(String)} is unbearably slow.
	 * Unfortunately, it also insists on using the {@link Writer} interface,
	 * which enforces synchronization.
	 * 
	 * TODO: we ignore the possibility of UTF-16 codepoints...
	 * 
	 * @param text
	 *            Text to process
	 * @return Text with entities replaced
	 */
	public static String removeEntities(String text) {
		if (text == null)
			return text;
		int i = text.indexOf('&');
		if (i < 0)
			return text;
		try {
			StringBuilder buf = new StringBuilder(text.length());
			int s = 0;
			final int end = text.length();
			while (i >= 0 && i < end) {
				buf.append(text, s, i);
				s = PREFIXMATCHER.match(text, i, end, buf);
				if (s == 0) {
					buf.append('&'); // No match
					++s;
				}
				s += i;
				i = text.indexOf('&', s);
			}
			if (s < text.length())
				buf.append(text, s, end);
			return buf.toString();
		} catch (IOException e) {
			// This should be unreachable, unless we run out of memory.
			throw new RuntimeException(e);
		}
	}

	/**
	 * Open a file, choosing a decompressor if necessary.
	 * 
	 * @param fname
	 *            Filename to open
	 * @return Input stream
	 * @throws FileNotFoundException
	 *             When the file does not exist
	 */
	public static InputStream openInput(String fname)
			throws FileNotFoundException {
		InputStream fin = new FileInputStream(fname);
		try {
			BufferedInputStream bis = new BufferedInputStream(fin);
			return new CompressorStreamFactory()
					.createCompressorInputStream(bis);
		} catch (CompressorException e) {
			return fin;
		}
	}

	/**
	 * Normalize a Wikipedia link.
	 *
	 * @param targ
	 *            Link target
	 * @return Normalized link (anchor removed, first char uppercase)
	 */
	public static String normalizeLink(String targ) {
		targ = targ.replace('\n', ' ').trim();
		if (targ.length() == 0)
			return null;
		char first = targ.charAt(0);
		if (Character.isLowerCase(first))
			targ = Character.toUpperCase(first) + targ.substring(1);
		return targ;
	}

	public static void main(String[] args) {
		// System.out.print(PREFIXMATCHER.debug(new StringBuilder(), 0));
		System.err.println(removeEntities("&amp;/or"));
		System.err.println(removeEntities("no match"));
		System.err.println(removeEntities("&lt;foo&gt;"));
		System.err.println(removeEntities("&lt;&gt;"));
		System.err.println(removeEntities("a&b"));
		System.err.println(removeEntities("&"));
		System.err.println(removeEntities("Con O&#039;Neill (diplomat)"));
		System.err.println(removeEntities("&#039ab&#039"));

		Matcher redirmatcher = Pattern
				.compile(
						"#REDIRECT[:\\s]*\\[\\[\\s*([^\\]\\[\\|]*?)\\s*(?:\\|\\s*[^\\]\\[\\#]*)?(?:#.*?)?\\s*\\]\\]",
						Pattern.CASE_INSENSITIVE).matcher("");

		redirmatcher.reset("#REDIRECT [[A♯ (musical note)|A{{music|sharp}}]]");
		if (redirmatcher.matches())
			System.err.println(">" + redirmatcher.group(1) + "<");
		redirmatcher
				.reset(removeEntities("#REDIRECT [[Con O&#039;Neill (diplomat)]]"));
		if (redirmatcher.matches())
			System.err.println(">" + redirmatcher.group(1) + "<");

		Matcher linkMatcher = Pattern
				.compile(
						"\\[\\[\\s*([^\\]\\[\\|]*?)\\s*(?:\\|\\s*([^\\]\\[\\#]*))?(?:#.*?)?\\s*\\]\\]")
				.matcher("");
		linkMatcher.reset(removeEntities("lorem ipsum [[Obamacare ]]"));
		if (linkMatcher.find())
			System.err.println(">" + linkMatcher.group(1) + "<");
	}

	public static class StringBuilderWriter extends Writer {
		StringBuilder buf = new StringBuilder();

		private StringBuilderWriter() {
			super();
			lock = buf;
		}

		@Override
		public void write(char[] cbuf, int off, int len) throws IOException {
			// TODO Auto-generated method stub

		}

		@Override
		public void flush() throws IOException {
			// TODO Auto-generated method stub

		}

		@Override
		public void close() throws IOException {
			// TODO Auto-generated method stub

		}
	}
}
