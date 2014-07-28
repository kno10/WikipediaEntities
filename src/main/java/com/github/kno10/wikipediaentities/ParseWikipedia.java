package com.github.kno10.wikipediaentities;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

/**
 * Parse and index a complete Wikipedia dump.
 * 
 * @author Erich Schubert
 */
public class ParseWikipedia {
	/** Input file name. */
	private String fname;

	/** Classes to process articles */
	protected Handler handler;

	/** Step size for progress reporting */
	static int STEP = 10;

	/** Title attribute for redirects */
	static final QName titleqname = new QName("title");

	/** Pattern for recognizing redirects */
	Matcher redirmatcher = Pattern
			.compile(
					"#REDIRECT\\s*:?\\s*\\[\\[\\s*(?:([^\\]\\[\\|]*)\\s*\\|\\s*)?([^\\]\\[\\|]*)(?:#.*?)?\\s*\\]\\]",
					Pattern.CASE_INSENSITIVE).matcher("");

	/**
	 * Constructor.
	 * 
	 * @param fname
	 *            Source file name
	 * @param handler
	 *            Page handler
	 */
	public ParseWikipedia(String fname, Handler handler) {
		this.fname = fname;
		this.handler = handler;
	}

	private void run() throws IOException, XMLStreamException {
		final XMLInputFactory factory = XMLInputFactory.newInstance();

		InputStream fin2 = Util.openInput(fname);
		XMLEventReader eventReader = factory.createXMLEventReader(fin2);

		int c = 0; // Number of pages processed.
		long start = System.currentTimeMillis(), prev = start;
		while (eventReader.hasNext()) {
			XMLEvent event = eventReader.nextEvent();
			if (event.isStartElement()) {
				StartElement startElement = event.asStartElement();
				if (startElement.getName().getLocalPart() == "page") {
					parsePage(eventReader);
					if (++c % STEP == 0) {
						long now = System.currentTimeMillis();
						System.err
								.format("%d articles processed (%.2f/s average, %.2f/s current).\n",
										c, (c * 1000. / (now - start)),
										(STEP * 1000. / (now - prev)));
						prev = now;
						if (c == 20*STEP) STEP *= 10;
					}
					// if (c == 100000000) break;
				}
			}
		}

		fin2.close();
		handler.close();
	}

	private void parsePage(XMLEventReader eventReader)
			throws XMLStreamException {
		String title = null, text = null, redirect = null;
		boolean skip = false;
		while (eventReader.hasNext()) {
			XMLEvent event = eventReader.nextEvent();
			if (event.isEndElement()
					&& event.asEndElement().getName().getLocalPart() == "page") {
				break;
			}
			if (!skip && event.isStartElement()) {
				StartElement startElement = event.asStartElement();
				// Ignore non-main pages
				if (startElement.getName().getLocalPart() == "ns")
					skip |= !"0".equals(parseTextContents(eventReader));

				if (startElement.getName().getLocalPart() == "title")
					title = parseTextContents(eventReader);

				if (startElement.getName().getLocalPart() == "text")
					text = parseTextContents(eventReader);

				if (startElement.getName().getLocalPart() == "redirect")
					redirect = startElement.getAttributeByName(titleqname)
							.getValue();
			}
		}
		// Post-process page.

		if (!skip && redirect == null && text == null && title != null)
			System.err.println("No redirect or text, but title: " + title);
		// Ignore non-main pages
		if (skip || title == null || text == null)
			return;
		title = Util.removeEntities(title);
		// Skip boring "list of" pages
		if (title.startsWith("List of "))
			return;
		if (redirect != null) {
			redirmatcher.reset(text);
			if (redirmatcher.find()) {
				String g1 = redirmatcher.group(1), g2 = redirmatcher.group(2);
				g1 = g1 != null ? g1 : g2;
				redirect = Util.normalizeLink(g1, g2);
			} else {
				redirect = Util.removeEntities(redirect);
				System.err.println("No redirect in " + title + ": " + text);
			}
			handler.redirect(title, redirect);
			return;
		}
		text = Util.removeEntities(text);
		// More text normalizations:
		text = Util.normalizeText(text);
		handler.rawArticle(title, text);
	}

	private String parseTextContents(XMLEventReader eventReader)
			throws XMLStreamException {
		// Chances are that we'll only need one string.
		String ret = null;
		StringBuilder buf = null; // Fallback to a string builder
		while (eventReader.hasNext()) {
			XMLEvent event = eventReader.nextEvent();
			if (event.isEndElement())
				break;
			if (event.isCharacters()) {
				if (ret == null)
					ret = event.asCharacters().getData();
				else { // This codepath may be unnecessary.
					if (buf == null)
						buf = new StringBuilder(ret);
					buf.append(event.asCharacters().getData());
				}
			}
		}
		return buf != null ? buf.toString() : ret;
	}

	/**
	 * Run from command line.
	 * 
	 * @param args
	 *            Command line attributes
	 */
	public static void main(String[] args) {
		try {
			HandlerList h1 = new HandlerList(), h2 = new HandlerList();
			ParseWikipedia l = new ParseWikipedia(Config.get("loader.source"),
					h1);
			WikipediaLinkExtractor le = new WikipediaLinkExtractor(h2);

			h1.add(le);
			h1.add(new RedirectCollector(Config.get("redirects.output")));
			h1.add(new LuceneWikipediaIndexer(Config.get("indexer.dir")));
			h2.add(new LinkCollector(Config.get("links.output")));
			h2.add(new LuceneLinkTokenizer(Config.get("linktext.output")));
			l.run();
		} catch (IOException | XMLStreamException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
}
