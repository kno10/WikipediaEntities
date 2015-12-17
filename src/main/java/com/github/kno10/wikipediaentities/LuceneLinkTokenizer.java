package com.github.kno10.wikipediaentities;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.standard.ClassicFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.wikipedia.WikipediaTokenizer;

import com.github.kno10.wikipediaentities.util.CounterSet;
import com.github.kno10.wikipediaentities.util.FastStringReader;
import com.github.kno10.wikipediaentities.util.Util;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

/**
 * Tokenize link texts seen in Wikipedia, to build a list of common link titles.
 * Count how often each target occurs.
 *
 * @author Erich Schubert
 */
public class LuceneLinkTokenizer {
  /** Link text -> map(target -> count) */
  Map<String, CounterSet<String>> links = new HashMap<>();

  /** Articles that really exist */
  private ObjectOpenHashSet<String> articles = new ObjectOpenHashSet<>();

  /** Output file name */
  private String out;

  /** Minimum support to report */
  static final int MINSUPP = 3;

  /** Filter to only retain links with minimum support */
  private CounterSet.CounterFilter popularityFilter = new CounterSet.CounterFilter(MINSUPP);

  /** Redirect collector */
  private RedirectCollector r;

  /**
   * Constructor
   *
   * @param out Output file name
   * @param r Redirect collector
   */
  public LuceneLinkTokenizer(String out, RedirectCollector r) {
    this.out = out;
    this.r = r;
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
    /** Link text -> map(target -> count) */
    Map<String, CounterSet<String>> links = new HashMap<>();

    /** Articles that really exist */
    private ObjectOpenHashSet<String> articles = new ObjectOpenHashSet<>();

    /** Lucene Wikipedia tokenizer */
    WikipediaTokenizer tokenizer;

    /** Filtered token stream */
    TokenStream stream;

    /** Lucene character term attribute */
    CharTermAttribute termAtt;

    @Override
    public void rawArticle(String title, String text) {
      articles.add(title);
    }

    /** Buffer for tokenization */
    StringBuilder buf = new StringBuilder();

    public LinkHandler() {
      tokenizer = new WikipediaTokenizer();
      stream = tokenizer;
      // stream = new PorterStemFilter(tokenizer);
      stream = new ClassicFilter(stream);
      stream = new LowerCaseFilter(stream);
      termAtt = stream.addAttribute(CharTermAttribute.class);
    }

    @Override
    public void linkDetected(String title, String label, String target) {
      // Normalize the link text.
      try {
        buf.delete(0, buf.length());
        tokenizer.setReader(new FastStringReader(label));
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
        CounterSet<String> seen = links.get(label);
        if(seen == null) {
          seen = new CounterSet<>();
          links.put(label, seen);
        }
        seen.count(target);
      }
      catch(IOException e) { // Should never happen in FastStringReader
        e.printStackTrace();
      }
    }

    @Override
    public void close() {
      synchronized(LuceneLinkTokenizer.this) {
        Map<String, CounterSet<String>> plinks = LuceneLinkTokenizer.this.links;
        for(Map.Entry<String, CounterSet<String>> e : links.entrySet()) {
          CounterSet<String> pset = plinks.get(e.getKey());
          if(pset == null) {
            plinks.put(e.getKey(), e.getValue());
          }
          else {
            pset.update(e.getValue());
          }
        }
        links = null;
        LuceneLinkTokenizer.this.articles.addAll(articles);
        articles = null;
      }
    }
  }

  public void close() throws IOException {
    System.err.format("Closing %s output.\n", getClass().getSimpleName());
    PrintStream writer = Util.openOutput(out);
    // We sort everything here. This is expensive, but makes the output
    // files nicer to use in the future.
    ArrayList<String> keys = new ArrayList<>(links.keySet());
    Collections.sort(keys);
    ResolveRedirectsFilter resolveRedirects = new ResolveRedirectsFilter(r.transitiveClosure());
    FilterBySet articleFilter = new FilterBySet(articles);
    for(String key : keys) {
      popularityFilter.reset();
      CounterSet<String> counter = links.get(key);
      resolveRedirects.map = counter;
      for(ObjectIterator<Object2IntMap.Entry<String>> it = counter.object2IntEntrySet().fastIterator(); it.hasNext();) {
        Object2IntMap.Entry<String> entry = it.next();
        String ekey = entry.getKey();
        int ecount = entry.getIntValue();
        if(!resolveRedirects.execute(ekey, ecount) || //
        !popularityFilter.execute(ekey, ecount) || //
        !articleFilter.execute(ekey, ecount)) {
          it.remove();
        }
      }
      if(counter.size() == 0)
        continue;
      writer.append(key).append('\t').append(Integer.toString(popularityFilter.getSum()));
      for(CounterSet.Entry<String> val : counter.descending()) {
        int c = val.getCount();
        if(c * 10 < popularityFilter.getMax())
          continue;
        writer.append('\t').append(val.getKey());
        writer.append(':').append(Integer.toString(c));
      }
      writer.append('\n');
    }
    if(writer != System.out)
      writer.close();
  }

  /**
   * Filter to resolve redirects.
   *
   * @author Erich Schubert
   */
  public static final class ResolveRedirectsFilter {
    Map<String, String> redirects;

    Object2IntOpenHashMap<String> map;

    public ResolveRedirectsFilter(Map<String, String> redirects) {
      this.redirects = redirects;
    }

    public boolean execute(Object target, int count) {
      String t = redirects.get(target);
      if(t == null)
        return true;
      map.addTo(t, count);
      return false;
    }
  }

  /**
   * Retain only entries that point to a "proper" article.
   *
   * @author Erich Schubert
   */
  public static final class FilterBySet {
    /** Acceptance set */
    ObjectOpenHashSet<String> accept;

    /**
     * Constructor
     *
     * @param accept Entries to accept.
     */
    public FilterBySet(ObjectOpenHashSet<String> accept) {
      this.accept = accept;
    }

    public boolean execute(Object a, int b) {
      return accept.contains(a);
    }
  }
}
