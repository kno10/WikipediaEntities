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
	 * @param title
	 *            Source title
	 * @param redirect
	 *            Redirection target
	 */
	void redirect(String title, String redirect);

	/**
	 * Process a raw article.
	 * 
	 * @param title
	 *            Article title
	 * @param text
	 *            Article text
	 */
	void rawArticle(String title, String text);

	/**
	 * A new link was detected in an article
	 * 
	 * @param title
	 *            Article title
	 * @param label
	 *            Link label
	 * @param target
	 *            Link target
	 */
	void linkDetected(String title, String label, String target);

	/**
	 * Processing has finished. Cleanup and close.
	 */
	void close();
}
