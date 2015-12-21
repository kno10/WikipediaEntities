package com.github.kno10.wikipediaentities;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import com.github.kno10.wikipediaentities.util.Progress;
import com.github.kno10.wikipediaentities.util.Util;

/**
 * Parse and index a complete Wikipedia dump.
 *
 * @author Erich Schubert
 */
public class ParseWikipedia {
  int readers = 0;

  Progress readprog = new Progress("Reading articles");

  Progress parseprog = new Progress("Parsing articles");

  /** Title attribute for redirects */
  static final QName TITLEQNAME = new QName("title");

  /**
   * Constructor.
   */
  public ParseWikipedia() {
    super();
  }

  /**
   * Start a new reader thread.
   *
   * @param fname Filename
   * @param prefix Prefix
   * @param queue Processing queue
   * @return reader thread
   */
  public synchronized Thread makeReaderThread(String fname, String prefix, BlockingQueue<Article> queue) {
    ++readers;
    return new ReaderThread(fname, prefix, queue);
  }

  /**
   * Thread for reading Wikipedia articles.
   *
   * @author Erich Schubert
   */
  private class ReaderThread extends Thread {
    /** Input file name. */
    private String fname;

    /** Naming prefix */
    private String prefix;

    /** Classes to process articles */
    protected BlockingQueue<Article> queue;

    /** String buffer */
    StringBuilder buf = new StringBuilder();

    /**
     * Constructor.
     *
     * @param fname Source file name
     * @param prefix Prefix
     * @param queue Queue to output articles to
     */
    public ReaderThread(String fname, String prefix, BlockingQueue<Article> queue) {
      this.fname = fname;
      this.prefix = prefix;
      this.queue = queue;
    }

    @Override
    public void run() {
      final XMLInputFactory factory = XMLInputFactory.newInstance();
      try (InputStream fin2 = Util.openInput(fname)) {
        XMLEventReader eventReader = factory.createXMLEventReader(fin2, "UTF-8");

        while(eventReader.hasNext()) {
          XMLEvent event = eventReader.nextEvent();
          if(event.isStartElement()) {
            StartElement startElement = event.asStartElement();
            if(startElement.getName().getLocalPart() == "page") {
              parsePage(eventReader);
              // if (readprog.get() == 10000) break;
            }
          }
        }
      }
      catch(IOException | XMLStreamException e) {
        throw new RuntimeException(e);
      }
      catch(InterruptedException e) {
        System.err.println("Processing interrupted.");
      }
      finally {
        // Update max, for progress logging.
        parseprog.setMax(readprog.get());

        // We've finished adding to the queue. Wait for shutdown.
        synchronized(ParseWikipedia.this) {
          --readers;
        }
      }
    }

    private void parsePage(XMLEventReader eventReader) throws XMLStreamException, InterruptedException {
      String title = null, text = null, redirect = null;
      boolean skip = false;
      while(eventReader.hasNext()) {
        XMLEvent event = eventReader.nextEvent();
        if(event.isEndElement() && event.asEndElement().getName().getLocalPart() == "page") {
          break;
        }
        if(!skip && event.isStartElement()) {
          StartElement startElement = event.asStartElement();
          // Ignore non-main pages
          if(startElement.getName().getLocalPart() == "ns")
            skip |= !"0".equals(parseTextContents(eventReader));

          if(startElement.getName().getLocalPart() == "title")
            title = parseTextContents(eventReader);

          if(startElement.getName().getLocalPart() == "text")
            text = parseTextContents(eventReader);

          if(startElement.getName().getLocalPart() == "redirect")
            redirect = startElement.getAttributeByName(TITLEQNAME).getValue();
        }
      }
      // Post-process page.
      if(!skip && redirect == null && text == null && title != null)
        System.err.println("No redirect or text, but title: " + title);
      // Ignore non-main pages
      if(skip || title == null || text == null)
        return;
      queue.put(new Article(prefix, title, redirect, text));
      readprog.incrementAndLog();
    }

    private String parseTextContents(XMLEventReader eventReader) throws XMLStreamException {
      // Chances are that we'll only need one string.
      String ret = null;
      buf.setLength(0);
      while(eventReader.hasNext()) {
        XMLEvent event = eventReader.nextEvent();
        if(event.isEndElement())
          break;
        if(event.isCharacters()) {
          if(ret == null)
            ret = event.asCharacters().getData();
          else { // Need to use the buffer
            if(buf.length() == 0)
              buf.append(ret);
            buf.append(event.asCharacters().getData());
          }
        }
      }
      if(buf.length() > 0) {
        ret = buf.toString();
      }
      return ret;
    }

  }

  public Thread makeParserThread(BlockingQueue<Article> q, Handler h) {
    return new WikipediaParserThread(q, h);
  }

  /**
   * Thread for parsing Wikipedia articles.
   *
   * @author Erich Schubert
   */
  private class WikipediaParserThread extends Thread {
    BlockingQueue<Article> queue;

    Handler handler;

    /** Pattern for recognizing redirects */
    private Matcher redirmatcher = Pattern.compile("#(?:REDIRECT|WEITERLEITUNG|REDIRECCI[oOÓó]N|REDIRECTION)[:,\\s]*\\[\\[\\s*([^\\]\\[\\|#]*?)(?:#\\s*(.*?)\\s*)?(?:\\s*\\|\\s*[^\\]\\[]*)?\\s*\\]\\]", Pattern.CASE_INSENSITIVE).matcher("");

    /**
     * Constructor.
     *
     * @param q Queue
     * @param h Handler
     */
    public WikipediaParserThread(BlockingQueue<Article> q, Handler h) {
      this.queue = q;
      this.handler = h;
    }

    @Override
    public void run() {
      while(!queue.isEmpty() || readers > 0) {
        try {
          Article a = queue.poll(100, TimeUnit.MILLISECONDS);
          if(a == null)
            continue;
          process(a);
          parseprog.incrementAndLog();
        }
        catch(InterruptedException e) {
          break;
        }
      }
      // System.err.println("Parser thread has completed.");
      handler.close();
    }

    private void process(Article a) {
      String title = Util.removeEntities(a.title);
      // Skip boring "list of" pages
      if(title.startsWith("List ") || title.startsWith("Liste ") || title.startsWith("Anexo:"))
        return;
      String text = Util.removeEntities(a.rawtext);
      if(a.redirect != null) {
        redirmatcher.reset(text);
        String anchor = "", redirect;
        if(redirmatcher.find()) {
          String g1 = redirmatcher.group(1);
          redirect = Util.normalizeLink(g1);
          anchor = redirmatcher.group(2);
        }
        else {
          redirect = Util.removeEntities(a.redirect);
          redirect = Util.normalizeLink(redirect);
          System.err.println("No redirect in " + title + ": " + text);
        }
        handler.redirect(a.prefix, title, redirect, anchor);
        return;
      }
      // Note: removing some of these too early will break redirects!
      text = Util.removeSpecial(text);
      handler.rawArticle(a.prefix, title, text);
    }
  }

  /**
   * Run from command line.
   *
   * @param args Command line attributes
   */
  public static void main(String[] args) {
    int par = Math.min(Integer.valueOf(Config.get("parallelism")), Runtime.getRuntime().availableProcessors());
    if(par < 1) {
      throw new Error("At least 1 consumer must be allowed!");
    }
    LuceneWikipediaIndexer indexer = null;
    try {
      List<Thread> threads = new ArrayList<>();

      BlockingQueue<Article> q1 = new ArrayBlockingQueue<>(100);
      ParseWikipedia l = new ParseWikipedia();
      // Start the reader:
      for(String s : Config.get("loader.source").split(",")) {
        String p = new File(s).getName().split("-")[0] + ":";
        Thread reader = l.makeReaderThread(s, p, q1);
        threads.add(reader);
      }
      RedirectCollector r = new RedirectCollector(Config.get("redirects.output"));
      indexer = new LuceneWikipediaIndexer(Config.get("indexer.dir"));
      LinkCollector lc = new LinkCollector(Config.get("links.output"));
      LuceneLinkTokenizer lt = new LuceneLinkTokenizer(Config.get("linktext.output"));
      System.err.println("Starting " + par + " worker threads.");
      for(int i = 0; i < par; i++) {
        HandlerList h = new HandlerList(), h2 = new HandlerList();
        Thread a = l.makeParserThread(q1, h);
        h.add(r.makeThreadHandler());
        h.add(indexer.makeThreadHandler(h2));
        h2.add(lc.makeThreadHandler());
        h2.add(lt.makeThreadHandler());
        threads.add(a);
      }

      // Start all:
      for(Thread th : threads)
        th.start();
      // Wait for all:
      for(Thread th : threads)
        try {
          th.join();
        }
        catch(InterruptedException e) {
          e.printStackTrace();
        }
      // Close in a controlled order:
      r.close(); // Before lt!
      indexer.close(); // Before lc, lt!
      lc.close();
      lt.close();
    }
    catch(IOException e) {
      e.printStackTrace();
      System.exit(1);
    }
  }
}
