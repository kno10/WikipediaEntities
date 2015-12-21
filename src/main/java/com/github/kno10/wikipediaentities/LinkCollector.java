package com.github.kno10.wikipediaentities;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;

import com.github.kno10.wikipediaentities.util.Util;

/**
 * Collect all outgoing internal links from each Wikipedia article.
 *
 * @author Erich Schubert
 */
public class LinkCollector {
  /** Output writer */
  PrintStream writer;

  /**
   * Constructor
   *
   * @param out Output stream
   * @throws IOException When output file cannot be created
   */
  public LinkCollector(String out) throws IOException {
    writer = Util.openOutput(out);
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
    /** Current page name. */
    String curp = null, cur = null;

    /** Observed link targets in current page */
    ArrayList<String> targets = new ArrayList<>();

    @Override
    public void linkDetected(String prefix, String title, String label, String target) {
      if(!prefix.equals(curp) || !title.equals(cur))
        nextEntry(prefix, title);
      targets.add(label.replace('\t', ' '));
      targets.add(target.replace('\t', ' '));
    }

    /**
     * Finish the current entry, proceed to the next.
     *
     * @param prefix Wiki prefix
     * @param next Next entry name
     */
    private void nextEntry(String prefix, String next) {
      synchronized(writer) {
        // Write and close previous entry
        if(cur != null) {
          writer.append(curp);
          writer.append(cur);
          for(String s : targets)
            writer.append('\t').append(s);
          writer.append('\n');
        }
      }
      curp = prefix;
      cur = next;
      targets.clear();
    }

    @Override
    public void close() {
      nextEntry(null, null);
      targets.clear();
    }
  }

  public void close() {
    System.err.format("Closing %s output.\n", getClass().getSimpleName());
    if(writer != System.out)
      writer.close();
  }
}
