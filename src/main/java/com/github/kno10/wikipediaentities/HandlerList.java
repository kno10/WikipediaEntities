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
	public void redirect(String prefix, String title, String redirect, String anchor) {
		for (Handler h : handlers)
			h.redirect(prefix, title, redirect, anchor);
	}

	@Override
	public void rawArticle(String prefix, String title, String text) {
		for (Handler h : handlers)
			h.rawArticle(prefix, title, text);
	}

	@Override
	public void linkDetected(String prefix, String title, String label, String target) {
		for (Handler h : handlers)
			h.linkDetected(prefix, title, label, target);
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
