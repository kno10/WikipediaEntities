Wikipedia Entities and Synonyms
===============================

Derive named entities and synonyms from Wikipedia.


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
Once we're confident in the output, it will be uploaded to a stable repository at
the university library, where it will also be given a DOI and we will provide
you a BibTeX snippet!

The source code here is made available for your to understand and reproduce the
results as necessary. But hopefully, you will not need to run it yourself.
To run this, you will need about 30 GB of disk space and 24 GB of RAM. The first
step takes one hour on a modern multicore PC; it produces 1.2 GB of link data,
and a 14 GB Lucene index. 66 MB of frequent link text and 120 MB of redirect
information. The second phase then takes about 45 minutes, and yields 5-10 MB
of entities depending on your thresholds.

We try to keep output data compressed. When dealing with large volume data,
this quickly pays off: text compresses very well, often to 20% or less. The
easiest to use compression is GZip, because it is available in the standard
Java API, and there is a massive set of tools available that can handle this
compression transparently.  For example `zgrep` can be used to search in these
files, without decompressing them - in fact, you should never have to
decompress them in my experience!


`links.gz`: the first column is the source page name, the remaining columns are
link texts followed by destination names (everything separated by `\t`, so you
should see an odd number of columns in every line).
Redirects have not yet been resolved.

Example:

    Patient Protection and Affordable Care Act\t	Internal Revenue Code\t	Internal Revenue Code\t	42 U.S.C.\t	Title 42 of the United States Code\t	...

In this example, the text `42 U.S.C.` is the link text, followed by the destination.

`redirects.gz`: the first column is the page name, the second column is the
transitive target (except for cyclic references), and the third column is an anchor, if given.
Example:

    Obamacare\t	Patient Protection and Affordable Care Act

`linktext.gz`: the first column is the tokenized link text (lowercased due to tokenization),
the second column is the total number of times this link text has been observed, followed
by the most popular destinations separated by `\t`. Rare destinations have been
omitted to reduce file size. Redirects have been resolved. Each destination is postfixed with the count,
how often this has been observed.
Example:

    obamacare\t	88\t	Patient Protection and Affordable Care Act:87

The second phase will then return a file containing common phrases, and their most common outcomes.
We report the number of articles the text was found in, and a score for each target. For the score,
we count how often the exact link text occurred + the number of articles that linked to the article
in question that contained the query phrase somewhere. This puts double weight on cases where the
link text is exactly the phrase, and thus the score can go up to 200%.

    obamacare\t	313\t	Patient Protection and Affordable Care Act:216:69%

If there is a clear association with the first term, and a much less clear with
the second, then we can be rather sure this is a synonymous term, i.e.
Obamacare == Patient Protected and Affordable Care Act.

But beware, there are language specific pitfalls. For example

    bayern\t	4392\t	FC Bayern Munich:743:16%\t	Bavaria:713:16%

indicates that the term "Bayern" (German name of Bavaria) is also
very often referring to the soccer club FC Bayern (in the English Wikipedia).


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
on my desktop PC. This is acceptable for all my needs.

Some routines that were taking up excessive amounts of time (such as -
unfortunately - replacing HTML entites using Apache Commons Lang3) have been
wrapped into more efficient implementations.

We are using a streaming XML parser, as you cannot just build a DOM tree from
a 10 GB compressed (48.7 GB decompressed) file...

Note: this implementation may need 24 GB of RAM to run, because we keep a lot of
data in memory, and sort it at the very end when writing. By splitting the process
into a third (sorting) phase, memory usage could be reduced. But given the size of
the Lucene index (15 GB), extra memory will also improve runtimes a lot.


Dependencies
------------

While the use of external libraries yields to a mess in many projects ("jar
hell"), we rely a few of them in this project:

* Apache Commons Lang3, despire the performance issues, for parsing HTML entities
(but we wrote our own, much faster, matcher)

* Apache Commons Compress, for BZip2 decompression

* Lucene 5, for full text indexing

* Fastutil, which offers high-performance collections for primitive types
(and helps keeping memory usage in control).


License
-------

This project is AGPL-3 licensed. This will likely make your commercial lawyers
unhappy. You should never need to incorporate this source code into a commercial
product, but if you need we can talk about this. Note that GNU Trove is LGPL
licensed.

Copyleft is a viable way for collaborative software development. The best proof
is called Linux. Companies trying to persuade you to choose Apache and BSD
licenses often just want to be able to incorporate all your work and not give
back ever; this defeats the purpose for anything but low-level libraries.

This is "tit for that"-ware. Because I make my code available, you should also
make your improvements available.
