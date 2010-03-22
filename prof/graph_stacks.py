#!/usr/bin/env python

import prof
import sys

prof.configPychart(name="mem", color=False)

nthreads = 8

options = {
    "arrayIndexStates" : "false", 
    "objectStates" : "false", 
    "profile" : "true", 
    "arrayCacheSize" : "16", 
    "lockCacheSize" : "4", 
    "traces" : "false", 
    "record" : "true", 
    "create" : "false", 
    "instrumentFullJDK" : "false", 
    "bytecodeDump" : "false", 
    "verify" : "false", 
    "preVerify" : "false", 
    "frames" : "false"
}

def jgfName(name):
    # remove "JGF" from head, "BenchSize_" from end
    return name if not name.startswith("JGF") else name[3:-10]

profs = prof.loadAll(sys.argv[1:], 
                     filename_filter=(lambda fn: not fn.endswith("warmup.py")),
                     prof_filter=(lambda p: prof.matchOptions(options, p)))
bench_profs = prof.partition(lambda p: jgfName(p["mainClass"]), profs)

print 'Segment size:'
for name, bp in bench_profs:
    size = prof.distAverage(*[prof.getStackSegmentLengthDist(p) for p in bp])
    print '  %s: %f' % (name, size)

print 'Stack depth (in methods / in segments):'
for name, bp in bench_profs:
    methods = prof.distAverage(*[prof.getStackDepthDist(p) for p in bp])
    segments = prof.distAverage(*[prof.getStackSegmentCountDist(p) for p in bp])
    print '  %s: %f / %f' % (name, methods, segments)
