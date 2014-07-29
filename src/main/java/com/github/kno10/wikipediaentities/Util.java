package com.github.kno10.wikipediaentities;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.lang3.StringEscapeUtils;

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

	/**
	 * Fast replace all HTML entities, because Apache commons
	 * {@link StringEscapeUtils#unescapeHtml4(String)} is unbearably slow.
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
			StringWriter buf = new StringWriter();
			int s = 0;
			final int end = text.length();
			while (i >= 0 && i < end) {
				buf.append(text, s, i);
				s = StringEscapeUtils.UNESCAPE_HTML4.translate(//
						text, i, buf);
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
	 * Normalize text by substituting a number of unusual chars with more usual
	 * alternatives.
	 * 
	 * @param text
	 *            Text to process.
	 * @return Substituted text
	 */
	public static String normalizeText(String text) {
		StringBuilder buf = new StringBuilder();
		for (int i = 0, l = text.length(); i < l; ++i) {
			char c = text.charAt(i);
			switch (c) {
			case '–':
				buf.append('-');
				break;
			case '—':
				buf.append('-');
				break;
			case '`':
				buf.append('\'');
				break;
			case '’':
				buf.append('\'');
				break;
			case '\t':
				buf.append(' ');
				break;
			default: // else keep
				buf.append(c);
			}
		}
		return buf.toString();
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
		if (targ.length() == 0)
			return null;
		int pos = targ.indexOf('#');
		if (pos == 0)
			return null;
		if (pos >= 0)
			targ = targ.substring(0, pos);
		char first = targ.charAt(0);
		if (Character.isLowerCase(first))
			targ = Character.toUpperCase(first) + targ.substring(1);
		return targ;
	}

	public static void main(String[] args) {
		System.err.println(removeEntities("&amp;/or"));
		System.err.println(removeEntities("no match"));
		System.err.println(removeEntities("&lt;foo&gt;"));
		System.err.println(removeEntities("&lt;&gt;"));
		System.err.println(removeEntities("a&b"));
		System.err.println(removeEntities("&"));

		Matcher redirmatcher = Pattern
				.compile(
						"#REDIRECT[:\\s]*\\[\\[\\s*([^\\]\\[\\|]*?)\\s*(?:\\|\\s*[^\\]\\[\\#]*)?(?:#.*?)?\\s*\\]\\]",
						Pattern.CASE_INSENSITIVE).matcher("");
		
		redirmatcher.reset("#REDIRECT [[A♯ (musical note)|A{{music|sharp}}]]");
	}
}
