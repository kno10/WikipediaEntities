package com.github.kno10.wikipediaentities.util;

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

	protected static PrefixTreeMatcher MATCHER, SMATCHER;

	// Build the prefix tree
	static {
		MATCHER = PrefixTreeMatcher.makeNumericalEntityMatcher();
		for (String[] p : EntityArrays.BASIC_UNESCAPE())
			MATCHER.add(p[0], p[1]);
		for (String[] p : EntityArrays.ISO8859_1_UNESCAPE())
			MATCHER.add(p[0], p[1]);
		for (String[] p : EntityArrays.HTML40_EXTENDED_UNESCAPE())
			MATCHER.add(p[0], p[1]);

		SMATCHER = new PrefixTreeMatcher();
		SMATCHER.add("–", "-");
		SMATCHER.add("—", "-");
		SMATCHER.add("`", "'");
		SMATCHER.add("’", "'");
		SMATCHER.add("\t", " ");
		SMATCHER.add("\r\n", "\n");
		SMATCHER.add("\r", "\n");
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
		try {
			StringBuilder buf = new StringBuilder(text.length());
			final int end = text.length();
			for (int s = 0; s >= 0 && s < end;) {
				int c = MATCHER.match(text, s, end, buf);
				if (c > 0)
					s += c;
				else
					buf.append(text.charAt(s++)); // No match
			}
			return buf.toString();
		} catch (IOException e) {
			// This should be unreachable, unless we run out of memory.
			throw new RuntimeException(e);
		}
	}

	public static String removeSpecial(String text) {
		if (text == null)
			return text;
		try {
			StringBuilder buf = new StringBuilder(text.length());
			final int end = text.length();
			for (int s = 0; s >= 0 && s < end;) {
				int c = SMATCHER.match(text, s, end, buf);
				if (c > 0)
					s += c;
				else
					buf.append(text.charAt(s++)); // No match
			}
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
			return new CompressorStreamFactory(true)
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
}
