package com.github.kno10.wikipediaentities;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Meta handler that proxies the events to everybody on the list.
 * 
 * @author Erich Schubert
 */
public class HandlerList implements Handler {
	/** Handler list. */
	Collection<Handler> handlers = new ArrayList<>();

	@Override
	public void redirect(String title, String redirect) {
		for (Handler h : handlers)
			h.redirect(title, redirect);
	}

	@Override
	public void rawArticle(String title, String text) {
		for (Handler h : handlers)
			h.rawArticle(title, text);
	}

	@Override
	public void linkDetected(String title, String label, String target) {
		for (Handler h : handlers)
			h.linkDetected(title, label, target);
	}

	@Override
	public void close() {
		for (Handler h : handlers)
			h.close();
	}

	/**
	 * Add a sub-handler to this handler.
	 * 
	 * @param h
	 *            Handler.
	 */
	public void add(Handler h) {
		handlers.add(h);
	}
}
