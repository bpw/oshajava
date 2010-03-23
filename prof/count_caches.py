#!/usr/bin/env python

import prof
import sys

options = {
    "profile" : "true", 
    "record" : "true"
}

def jgfName(name):
    # remove "JGF" from head, "BenchSize_" from end
    return name if not name.startswith("JGF") else name[3:-10]

assert len(sys.argv) > 1
all = prof.loadAll(sys.argv[1:], 
                   filename_filter=(lambda fn: not fn.endswith("warmup.py") and not fn.startswith("JGFMonteCarlo")),
                   prof_filter=(lambda p: prof.matchOptions(options, p)))

def count((name, profiles)):
    array, index = prof.bisect(lambda p: prof.matchOptions({"arrayIndexStates" : "false"}, p), profiles)
    ah = sum(prof.mapToField("Array hits", array))
    am = sum(prof.mapToField("Array misses", array))
    a = float(ah) / float(ah + am)
    ih = sum(prof.mapToField("Array hits", index))
    im = sum(prof.mapToField("Array misses", index))
    i = float(ih) / float(ih + im)
    return (name, a, i)

print '(name, array hit rate (array states), array hit rate (array index states))'
print map(count, prof.partition(lambda p: jgfName(p["mainClass"]), all))
print count(('all', all))
