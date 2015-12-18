package com.github.kno10.wikipediaentities;

/**
 * Class representing an article.
 *
 * @author Erich Schubert
 */
public class Article {
  /** Original content */
  String prefix, title, redirect, rawtext;

  /**
   * Constructor.
   *
   * @param prefix Prefix
   * @param title Title
   * @param redirect Redirect
   * @param rawtext Rawtext
   */
  public Article(String prefix, String title, String redirect, String rawtext) {
    super();
    this.prefix = prefix;
    this.title = title;
    this.redirect = redirect;
    this.rawtext = rawtext;
  }
}
