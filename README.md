WikipediaEntities
=================

Derive named entities from Wikipedia.


This is a fairly simple approach for "learning" a corpus of named entities.

In the first pass, we parse a complete
[Wikipedia dump](http://dumps.wikimedia.org/enwiki/) in
`pages-articles.xml.bz2` format. From each regular article page we extract

* the redirect target, if the article is a redirect (redirects.gz)

* all links, except in Wikipedia templates and references (links.gz)

* all text used for linking to other articles (linktext.gz)

* full text of article for search (Lucene index, with stemming disabled)

In the second pass, we read all the redirects and page links, then iterate over
the known link texts. For every link text, we query the Lucene database for the
pages where this phrase occurs. Let's take for example the phrase "obamacare".
There are 251 articles in Wikipedia that use this phrase. One example is
[Ralph Hudgens](http://en.wikipedia.org/wiki/Ralph_Hudgens). For each page, we
resolve the outgoing links and redirects. This article links to Obamacare,
which is a redirect to [Patient Protection and Affordable Care Act](http://en.wikipedia.org/wiki/Patient_Protection_and_Affordable_Care_Act).
Thus, we count this article as supporting that Obamacare is an entity,
and the Wikipedia name of this entity is that final target. Of course the
article also includes links to other articles (e.g. the republican party);
but hopefully there will be much less agreement on them.


Output data
-----------

The plan is to make the output data available for use by other researchers.
We plan on providing a stable download location, as well as a citeable
reference (with DOI and all the niceties) to make it easy to use in scientific
work. Please bear with us until we've reached a stable data set. Thank you.


We try to keep all output data compressed. When dealing with large volume data, this
quickly pays off: text compresses very well, often to 20% or less. The easiest to
use compression is GZip, because it is available in the standard Java API, and there is
a massive set of tools available that can handle this compression transparently.
For example `zgrep` can be used to search in these files, without decompressing them -
in fact, you should never have to decompress them in my experience!


`links.gz`: the first column is the source page name, the remaining columns are
destination names (everything separated by `\t`). Redirects have not yet been resolved.
Example:

    Patient Protection and Affordable Care Act\t	105th United States Congress\t	111th United States Congress\t	...

`redirects.gz`: the first column is the page name, the last column is the
transitive target (except for cyclic references). Example:

    Obamacare\t	Patient Protection and Affordable Care Act

`linktext.gz`: the first column is the tokenized link text (lowercased due to tokenization),
the remaining columns are the observed link destinations, separated by `\t`.
Redirects have not yet been resolved. Example:

    obamacare\t	America's Affordable Health Choices Act of 2009\t	ObamaCare\t	Obamacare\t	Patient Protection and Affordable Care Act

The second phase will then return a file containing common phrases, and their most common outcomes:

    obamacare\t	251 \
    \t	Patient Protection and Affordable Care Act:195:0.777 \
    \t	Republican Party (United States):113:0.450 \
    \t	United States House of Representatives:107:0.426
    \t	Barack Obama:100:0.398

If there is a clear association with the first term, and a much less clear with
the second, then we can be rather sure this is a synonymous term, i.e.
Obamacare == Patient Protected and Affordable Care Act.

But beware, there are language specific pitfalls. For example

    bayern\t	3731\t	FC Bayern Munich:2167:0.581 ... Bavaria:566:0.152

yet, "Bayern" is in fact the german name of the state of Bavaria.
It is specific to English wikipedia that this term is mostly used in
conjunction with the soccer club FC Bayern.


Processing Wikipedia Data
-------------------------

There are redirect cycles. This happens in every Wikipedia snapshot; and usually they
are repaired soon after. But as Wikipedia is constantly changing, every snapshot has some
oddities and errors, including articles where the contents have been vandalized or removed,
and cyclic redirects.

Parsing Wikipedia is a pain. We're currently using a number of crude regular expressions,
because many of the parsers around (e.g. the Lucene Wikipedia parser) are even worse.
Wikipedia doesn't have a very clean or well-designed syntax; it has grown over the years.
It's much more complex than you might think, because of various nested structures. You can
have templates such as info boxes, which contain image links, which contain
templates, which contain links again... there also is a template for the music
"sharp" symbol, ♯: `{{music|sharp}}`, used in various places. And so on.

If you are looking for a powerful parser, please go to this site instead:
[Alternative MediaWiki parsers](https://www.mediawiki.org/wiki/Alternative_parsers)

We're really trying only to get the main 99% of Wikipedia contents, and we're okay with
losing 1% of it.


Implementation notes
--------------------

I've tried hard to make the implementation fast and efficient, but there is
always room for improvement. Currently, the first phase takes around 2 hours
on my powerful desktop PC. This is acceptable for all my needs.

Some routines that were taking up excessive amounts of time (such as -
unfortunately - replacing HTML entites using Apache Commons Lang3) have been
wrapped into more efficient implementations.

We are using a streaming XML parser, as you cannot just build a DOM tree from
a 10 GB compressed (48.7 GB decompressed) file...

Note: this implementation may need 16 GB of RAM to run, because we keep all the
page titles and links in memory. This could be reduced by storing the outgoing
links in the Lucene index, for example.



Dependencies
------------

While I'm not a big fan of external libraries, we use a few of them here

* Apache Commons Lang3, despire the performance issues, for parsing HTML entities

* Apache Commons Compress, for BZip2 decompression

* Lucene 3.6, for full text indexing
