package com.github.kno10.wikipediaentities.util;

import java.io.IOException;
import java.io.Reader;

/**
 * Fast implementation of the {@link Reader} API for Strings.
 * 
 * Avoids locking.
 * 
 * @author Erich Schubert
 */
public class FastStringReader extends Reader {
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