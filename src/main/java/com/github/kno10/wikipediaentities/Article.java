package com.github.kno10.wikipediaentities;

/**
 * Class representing an article.
 * 
 * @author Erich Schubert
 */
public class Article {
	/** Original content */
	String title, redirect, rawtext;

	/**
	 * Constructor.
	 * 
	 * @param title
	 *            Title
	 * @param redirect
	 *            Redirect
	 * @param rawtext
	 *            Rawtext
	 */
	public Article(String title, String redirect, String rawtext) {
		super();
		this.title = title;
		this.redirect = redirect;
		this.rawtext = rawtext;
	}
}
