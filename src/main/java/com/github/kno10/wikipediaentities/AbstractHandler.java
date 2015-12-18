package com.github.kno10.wikipediaentities;

/**
 * Abstract handler used in this toolkit.
 *
 * @author Erich Schubert
 */
public abstract class AbstractHandler implements Handler {
	@Override
	public void rawArticle(String prefix, String title, String text) {
		// Ignore
	}

	@Override
	public void redirect(String prefix, String title, String redirect, String anchor) {
		// Ignore
	}

	@Override
	public void linkDetected(String prefix, String title, String label, String target) {
		// Ignore
	}

	@Override
	public void close() {
		// Ignore
	}
}
