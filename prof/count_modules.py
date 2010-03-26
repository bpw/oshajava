#!/usr/bin/env python

import prof
import static

import sys
import os

statses = static.load_all(sys.argv[1:])

out = []
all_uninlined = 0
all_modules = 0
for name, stats in statses:
    modules = sum(static.getMethods(stats).values())
    inlined = prof.distTotal(static.getInlined(stats))
    methods = prof.distTotal(static.getMethods(stats))
    
    withzerogroups = static.getCGroups(stats).get(0, 0)
    modules -= withzerogroups
    
    avg = float(methods-inlined) / modules if modules else 0
    
    out.append((static.BENCH_NAMES[name], modules, avg))
    all_uninlined += methods - inlined
    all_modules += modules
    
    print '%s : %i , %i / %i' % (name, modules, methods-inlined, methods)
print 'Num nonempty modules, average uninlined methods per nonempty module:', out
print 'Overall uninlined methods per nonempty module:', float(all_uninlined) / all_modules
