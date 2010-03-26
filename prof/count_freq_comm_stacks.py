#!/usr/bin/env python

import prof
import sys

options = {
    "profile" : "true", 
    "record" : "true"
}

def getName(p):
    if p["mainClass"].startswith("JGF"):
        # remove "JGF" from head, "BenchSize_" from end
        return p["mainClass"][3:-10]
    elif p["mainClass"] == "Harness":
        return p["options"]["profileExt"].split('-')[1]

assert len(sys.argv) > 1
all = prof.loadAll(sys.argv[1:], 
                   filename_filter=(lambda fn: not fn.endswith("warmup.py")),
                   prof_filter=(lambda p: prof.matchOptions(options, p)))

def count((name, profiles)):
    a = max(map(lambda p: p["frequently communicating stacks"], profiles))
    return (name, a)

print "Frequently communicating call stacks"
print map(count, prof.partition(getName, all))
print count(('all', all))
