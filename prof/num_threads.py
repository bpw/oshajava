#!/usr/bin/env python

import prof
import sys

assert len(sys.argv) > 1
def getName(p):
    return p["options"]["profileExt"].split('-')[1].capitalize()
bench = prof.partition(getName,prof.loadAll(sys.argv[1:]))

for n,profs in bench:
    print n, ':', [p["threads"] for p in profs]
