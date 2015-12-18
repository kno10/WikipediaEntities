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
  /** Text to retrieve */
  CharSequence text;

  /** Position and remaining */
  int p, r;

  public FastStringReader(CharSequence text) {
    this.text = text;
    this.p = 0;
    this.r = text.length();
  }

  public Reader reset(CharSequence text) {
    this.text = text;
    this.p = 0;
    this.r = text.length();
    return this;
  }

  @Override
  public int read(char[] cbuf, int off, int len) throws IOException {
    int c = len < r ? len : r; // Number of characters to copy
    if(c == 0)
      return -1;
    if(text instanceof String) {
      ((String) text).getChars(p, p + c, cbuf, off);
      p += c; // Position
      r -= c; // Remaining
    }
    else {
      r -= c;
      while(c-- > 0) {
        cbuf[off++] = text.charAt(p++);
      }
    }
    return c;
  }

  @Override
  public void close() throws IOException {
    text = null;
  }
}
