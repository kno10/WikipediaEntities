package com.github.kno10.wikipediaentities;

/**
 * Handle data processing events.
 *
 * @author Erich Schubert
 */
public interface Handler {
	/**
	 * Process a redirect.
	 *
	 * @param prefix
	 *            Wiki prefix
	 * @param title
	 *            Source title
	 * @param redirect
	 *            Redirection target
	 * @param anchor
	 *            Anchor
	 */
	void redirect(String prefix, String title, String redirect, String anchor);

	/**
	 * Process a raw article.
	 *
   * @param prefix
   *            Wiki prefix
	 * @param title
	 *            Article title
	 * @param text
	 *            Article text
	 */
	void rawArticle(String prefix, String title, String text);

	/**
	 * A new link was detected in an article
	 *
   * @param prefix
   *            Wiki prefix
	 * @param title
	 *            Article title
	 * @param label
	 *            Link label
	 * @param target
	 *            Link target
	 */
	void linkDetected(String prefix, String title, String label, String target);

	/**
	 * Processing has finished. Cleanup and close.
	 */
	void close();
}
