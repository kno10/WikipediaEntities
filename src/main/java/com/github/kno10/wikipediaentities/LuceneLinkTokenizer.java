package com.github.kno10.wikipediaentities;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.standard.ClassicFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.wikipedia.WikipediaTokenizer;

import com.github.kno10.wikipediaentities.util.FastStringReader;
import com.github.kno10.wikipediaentities.util.Util;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

/**
 * Tokenize link texts seen in Wikipedia, to build a list of common link titles.
 * Count how often each target occurs.
 *
 * @author Erich Schubert
 */
public class LuceneLinkTokenizer {
  /** Link text */
  Object2IntOpenHashMap<String> links = new Object2IntOpenHashMap<>();

  /** Output file name */
  private String out;

  /** Minimum support to report */
  static final int MINSUPP = 3;

  /**
   * Constructor
   *
   * @param out Output file name
   */
  public LuceneLinkTokenizer(String out) {
    this.out = out;
  }

  /**
   * Make handler for a single thread.
   *
   * @return Threadsafe handler.
   */
  public Handler makeThreadHandler() {
    return new LinkHandler();
  }

  class LinkHandler extends AbstractHandler {
    /** Link texts */
    Object2IntOpenHashMap<String> links = new Object2IntOpenHashMap<>();

    /** Lucene Wikipedia tokenizer */
    WikipediaTokenizer tokenizer;

    /** Filtered token stream */
    TokenStream stream;

    /** Lucene character term attribute */
    CharTermAttribute termAtt;

    /** Buffer for tokenization */
    StringBuilder buf = new StringBuilder();

    /** String reader */
    FastStringReader reader = new FastStringReader("");

    public LinkHandler() {
      tokenizer = new WikipediaTokenizer();
      stream = tokenizer;
      // stream = new PorterStemFilter(stream);
      stream = new ClassicFilter(stream);
      stream = new LowerCaseFilter(stream);
      termAtt = stream.addAttribute(CharTermAttribute.class);
    }

    @Override
    public void linkDetected(String prefix, String title, String label, String target) {
      // Normalize the link text.
      try {
        buf.delete(0, buf.length());
        tokenizer.reset();
        tokenizer.setReader(reader.reset(label));
        stream.reset();
        while(stream.incrementToken()) {
          if(termAtt.length() <= 0)
            continue;
          if(buf.length() > 0)
            buf.append(' ');
          buf.append(termAtt.buffer(), 0, termAtt.length());
        }
        if(buf.length() == 0)
          return;
        label = buf.toString();
        links.addTo(label, 1);
      }
      catch(IOException e) { // Should never happen in FastStringReader
        e.printStackTrace();
      }
    }

    @Override
    public void close() {
      synchronized(LuceneLinkTokenizer.this) {
        Object2IntOpenHashMap<String> plinks = LuceneLinkTokenizer.this.links;
        if(plinks.size() == 0) {
          LuceneLinkTokenizer.this.links = links;
        }
        else {
          for(ObjectIterator<Object2IntOpenHashMap.Entry<String>> it = links.object2IntEntrySet().fastIterator(); it.hasNext();) {
            Object2IntOpenHashMap.Entry<String> ent = it.next();
            plinks.addTo(ent.getKey(), ent.getIntValue());
          }
        }
        links = null;
      }
    }
  }

  public void close() throws IOException {
    System.err.format("Closing %s output.\n", getClass().getSimpleName());
    PrintStream writer = Util.openOutput(out);
    // We sort everything here. This is expensive, but makes the output
    // files nicer to use in the future.
    ArrayList<String> keys = new ArrayList<>(links.size());
    for(ObjectIterator<Object2IntOpenHashMap.Entry<String>> it = links.object2IntEntrySet().fastIterator(); it.hasNext();) {
      Object2IntOpenHashMap.Entry<String> ent = it.next();
      if(ent.getIntValue() >= MINSUPP) {
        keys.add(ent.getKey());
      }
    }
    Collections.sort(keys);
    for(String key : keys) {
      writer.append(key);
      writer.append('\n');
    }
    if(writer != System.out)
      writer.close();
  }
}
