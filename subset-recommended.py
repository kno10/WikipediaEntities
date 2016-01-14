#!/usr/bin/python
import gzip, re, sys

# Minimum phrase length (characters)
minlen = 3
# Minimum number of occurrences
mincount = 50
# Minimum trust value
mintrust = 90
mintrustexact = 80
# Results with exact matches only
exactonly = True
# Minimum contrast, i.e. second may have at most trust < besttrust-mincontrast
mincontrast = 20

# Match the percentage at the end only:
pat = re.compile(r"^(.*?):[0-9:]+:([0-9]+):([0-9]+)%$")

# Output to stdout:
ou = sys.stdout

for line in gzip.open("entities.gz"):
	line = line.split("\t")
	phrase, count, used = line[0], line[1], line[2]
	if used < mincount: continue
	if len(phrase) < minlen: continue
	m = pat.match(line[3])
	if not m:
		print >>sys.stderr, "Did not match:", line
		continue
	trust = float(m.group(3))
	isexact = not (m.group(2) == '0')
	if isexact:
		if trust < mintrustexact: continue
	else:
		if trust < mintrust: continue
	if exactonly and not isexact: continue
	if len(line) > 4:
		m2 = pat.match(line[4])
		if not m2:
			print >>sys.stderr, "Did not match:", line
			continue
		trust2 = float(m2.group(3))
		if trust2 >= trust - mincontrast:
			continue
	ou.write(phrase)
	ou.write("\t")
	ou.write(m.group(1))
	ou.write("\n")

