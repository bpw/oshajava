#!/usr/bin/env python

from pychart import *
import prof
import sys

prof.configPychart(name="checks", color=False)

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

bench_profs = prof.partition(lambda p: jgfName(p["mainClass"]),
                             prof.loadAll(sys.argv[1:], 
                                          filename_filter=(lambda fn: not fn.endswith("warmup.py")),
                                          prof_filter=(lambda p: prof.matchOptions(options, p))))

def checks((name, profiles)):
    total = float(sum(map(prof.getChecks, profiles)))
    print name, len(profiles)
    return (name, 
            float(sum(map(prof.getThreadLocalHits, profiles))) / total,
            float(sum(map(prof.getFastMemoHits, profiles))) / total,
            float(sum(map(prof.getSlowMemoHits, profiles))) / total,
            float(sum(map(prof.getStackWalks, profiles))) / total)
            

sd = map(checks, bench_profs)

print '(name, tl, fast memo, slow memo, walk)'
print sd
ystep = .1
canvas = area.T(x_coord = category_coord.T(sd, 0),
                x_axis=axis.X(label="Benchmarks", format="/a90%s"),
                y_axis=axis.Y(label="Fraction of Reads", tic_interval=ystep),
                y_grid_interval=ystep,
                y_range=(0,1))

tl = bar_plot.T(label="Thread-local", data=sd)
fast = bar_plot.T(label="Fast memo", data=sd, hcol=2, stack_on=tl)
slow = bar_plot.T(label="Slow memo", data=sd, hcol=3, stack_on=fast)
walk = bar_plot.T(label="Stack walk", data=sd, hcol=4, stack_on=slow)

canvas.add_plot(tl, fast, slow, walk)

canvas.draw()
