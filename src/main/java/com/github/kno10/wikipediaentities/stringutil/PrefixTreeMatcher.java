package com.github.kno10.wikipediaentities.stringutil;

import java.io.IOException;
import java.util.Arrays;

/**
 * Prefix tree for string substitution.
 * 
 * Note: this is <em>not</em> optimized for long strings, but for large sets of
 * small strings!
 * 
 * @author Erich Schubert
 */
public class PrefixTreeMatcher {
	/** Empty array */
	private static final char[] EMPTY_CHAR = new char[0];
	/** Empty array */
	private static final PrefixTreeMatcher[] EMPTY_MATCHERS = new PrefixTreeMatcher[0];
	/** Initial size */
	private static final int INITIAL_SIZE = 5;
	/** Number of characters in list */
	int size = 0;
	/** Prefix characters */
	char[] m = EMPTY_CHAR;
	/** Next level matchers */
	PrefixTreeMatcher[] t = EMPTY_MATCHERS;
	/** Substitution for an exact match */
	CharSequence rep;

	/**
	 * Add a string to the matcher.
	 * 
	 * @param in
	 *            Input string.
	 * @param out
	 *            Replacement string.
	 */
	public void add(CharSequence in, CharSequence out) {
		add(in, 0, in.length(), out);
	}

	/**
	 * Add a string to the matcher
	 * 
	 * @param in
	 *            Input sequence
	 * @param pos
	 *            Start
	 * @param length
	 *            Length
	 * @param out
	 *            Replacement
	 */
	private void add(CharSequence in, int pos, int len, CharSequence out) {
		if (pos == len) {
			if (rep != null && !rep.equals(out))
				throw new RuntimeException("Contradicting replacements.");
			rep = out;
			return;
		}
		findOrInsert(in.charAt(pos)).add(in, pos + 1, len, out);
	}

	/**
	 * Find the matching subtree, or insert a new one.
	 * 
	 * @param c
	 *            Character
	 * @return Subtree matcher
	 */
	private PrefixTreeMatcher findOrInsert(char c) {
		final PrefixTreeMatcher sub;
		if (m == EMPTY_CHAR) {
			m = new char[INITIAL_SIZE];
			t = new PrefixTreeMatcher[INITIAL_SIZE];
			m[0] = c;
			size = 1;
			return t[0] = new PrefixTreeMatcher();
		}
		int i = Arrays.binarySearch(m, 0, size, c);
		if (i < 0) {
			i = (-i) - 1;
			sub = new PrefixTreeMatcher();
			insert(i, c, sub);
			return sub;
		}
		return t[i];
	}

	/**
	 * Insert into the internal data structure
	 * 
	 * @param i
	 *            Insertion position
	 * @param c
	 *            Character to insert
	 * @param sub
	 *            Subtree matcher to insert
	 */
	private void insert(int i, char c, PrefixTreeMatcher sub) {
		final int len = m.length;
		if (size >= len) { // Grow
			int l = (len << 1) - 1;
			char[] m2 = new char[l];
			PrefixTreeMatcher[] t2 = new PrefixTreeMatcher[l];
			System.arraycopy(m, 0, m2, 0, i);
			m2[i] = c;
			System.arraycopy(m, i, m2, i + 1, len - i);
			System.arraycopy(t, 0, t2, 0, i);
			t2[i] = sub;
			System.arraycopy(t, i, t2, i + 1, len - i);
			m = m2;
			t = t2;
			++size;
			return;
		}
		// Insert - move others back
		System.arraycopy(m, i, m, i + 1, size - i);
		m[i] = c;
		System.arraycopy(t, i, t, i + 1, size - i);
		t[i] = sub;
		++size;
	}

	/**
	 * Try to match the current pattern.
	 * 
	 * @param seq
	 *            Character source
	 * @param pos
	 *            Current position
	 * @param len
	 *            Length of source
	 * @param out
	 *            Output buffer
	 * @return Number of consumed characters
	 * @throws IOException
	 *             when {@link Appendable#append} fails.
	 */
	public int match(CharSequence seq, int pos, int len, Appendable out)
			throws IOException {
		return match(seq, pos, len, out, 0);
	}

	/**
	 * Try to match the current pattern.
	 * 
	 * @param seq
	 *            Character source
	 * @param pos
	 *            Current position
	 * @param len
	 *            Length of source
	 * @param out
	 *            Output buffer
	 * @param i
	 *            Length of already matched sequence
	 * @return Number of consumed characters
	 * @throws IOException
	 *             when {@link Appendable#append} fails.
	 */
	protected int match(CharSequence seq, int pos, int len, Appendable out,
			int i) throws IOException {
		if (pos < len) {
			final char c = seq.charAt(pos);
			final int j = Arrays.binarySearch(m, 0, size, c);
			if (j >= 0) {
				final int consumed = t[j].match(seq, pos + 1, len, out, i + 1);
				if (consumed > 0)
					return consumed;
			}
		}
		if (rep != null) {
			out.append(rep);
			return i;
		}
		return 0;
	}

	/** Whitespace for faster debug printing. */
	final static String SPACES = "                                        ";
	/** Amount of whitespace */
	final static int SPACELEN = SPACES.length();

	/**
	 * Debug dump.
	 * 
	 * @param out
	 *            Output buffer
	 * @param depth
	 *            Current depth
	 */
	public StringBuilder debug(StringBuilder out, int depth) {
		out.append(rep).append('\n');
		for (int i = 0; i < size; ++i) {
			for (int d = depth; d > 0; d -= SPACELEN)
				out.append(SPACES, 0, d < SPACELEN ? d : SPACELEN);
			out.append(m[i]).append(": ");
			t[i].debug(out, depth + 1);
		}
		return out;
	}

	/**
	 * Build a matcher that recongized XML numerical entites.
	 * 
	 * @return Matcher.
	 */
	public static PrefixTreeMatcher makeNumericalEntityMatcher() {
		PrefixTreeMatcher t = new PrefixTreeMatcher();
		// Hack the named entity matcher into this...
		PrefixTreeMatcher tamp = t.findOrInsert('&');
		tamp.findOrInsert('#');
		tamp.t[0] = new NumericalEntityMatcher();
		return t;
	}

	/**
	 * Hack to match numerical HTML/XML entities.
	 * 
	 * Note: this must be injected as the "&#" node.
	 * 
	 * @author Erich Schubert
	 */
	public static class NumericalEntityMatcher extends PrefixTreeMatcher {
		@Override
		protected int match(CharSequence seq, int pos, int len, Appendable out,
				int i) throws IOException {
			int r = super.match(seq, pos, len, out, i);
			if (r > 0 || pos == len)
				return r;
			char c = seq.charAt(pos);
			if (c == 'x' || c == 'X') {
				++pos;
				++i;
				if (pos == len) // Catch end of string.
					return 0;
				return matchHex(seq, pos, len, out, i);
			} else
				return matchDecimal(seq, pos, len, out, i);
		}

		private int matchDecimal(CharSequence seq, int start, int len,
				Appendable out, int i) throws IOException {
			int pos = start;
			int buf = 0;
			char c = 0;
			while (pos < len) {
				c = seq.charAt(pos);
				if (c < '0' || c > '9')
					break;
				buf = buf * 10 + (c - '0');
				++pos;
			}
			if (pos == start)
				return 0;
			// Consume a final semicolon, too.
			if (c == ';')
				++pos;
			if (buf > 0xFFFF)
				try {
					for (char c2 : Character.toChars(buf))
						out.append(c2);
				} catch (java.lang.IllegalArgumentException e) {
					return 0;
				}
			else if (buf < 0)
				return 0;
			else
				out.append((char) buf);
			return i + (pos - start);
		}

		private int matchHex(CharSequence seq, int start, int len,
				Appendable out, int i) throws IOException {
			int pos = start;
			int buf = 0;
			char c = 0;
			while (pos < len) {
				c = seq.charAt(pos);
				if (c >= '0' && c <= '9') {
					buf = buf * 16 + (c - '0');
				} else if (c >= 'a' && c <= 'f') {
					buf = buf * 16 + (c - 'a' + 10);
				} else if (c >= 'A' && c <= 'F') {
					buf = buf * 16 + (c - 'A' + 10);
				} else
					break;
				++pos;
			}
			if (pos == start)
				return 0;
			// Consume a final semicolon, too.
			if (c == ';')
				++pos;
			if (buf > 0xFFFF)
				try {
					for (char c2 : Character.toChars(buf))
						out.append(c2);
				} catch (java.lang.IllegalArgumentException e) {
					return 0;
				}
			else if (buf < 0)
				return 0;
			else
				out.append((char) buf);
			return i + (pos - start);
		}

		@Override
		public StringBuilder debug(StringBuilder out, int depth) {
			out.append("<numerical entites>");
			super.debug(out, depth);
			return out;
		}
	}
}
