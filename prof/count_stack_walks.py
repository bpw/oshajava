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
    a = max(map(prof.getStackWalks, array))
    i = max(map(prof.getStackWalks, index))
    ac = max(map(prof.getComms, array))
    ic = max(map(prof.getComms, index))
    acm = min(map(prof.getComms, array))
    icm = min(map(prof.getComms, index))
    return (name, a, i, ac, ic, acm, icm)

print '(name, max walks array, max walks index, max comm array, max comm index, min comm array, min comm index)', map(count, prof.partition(lambda p: jgfName(p["mainClass"]), all))
print count(('all', all))
