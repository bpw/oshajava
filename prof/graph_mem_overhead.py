#!/usr/bin/env python

from pychart import *
import prof
import sys

prof.configPychart(name="mem", color=False)

nthreads = 8

options = {
    "arrayIndexStates" : "false", 
    "objectStates" : "false", 
    "profile" : "false", 
    "arrayCacheSize" : "16", 
    "lockCacheSize" : "4", 
    "traces" : "false", 
    "record" : "false", 
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

#thread_profs = prof.partition(lambda p: int(p["options"]["profileExt"].split("-")[1]),
profs = prof.loadAll(sys.argv[1:], 
                     filename_filter=(lambda fn: not fn.endswith("warmup.py")),
                     prof_filter=(lambda p: prof.matchOptions(options, p))) #)

def slowdown((name, profiles)):
    oshajava, java = prof.bisect(lambda p: prof.matchOptions({"noInstrument" : "false"}, p), profiles)
    oj = map(prof.getPeakMem, oshajava)
    j = map(prof.getPeakMem, java)
#    print name, oj, j
    return (name, float(sum(oj)) / float(sum(j)))

sd = sorted(map(slowdown, prof.partition(lambda p: jgfName(p["mainClass"]), profs)))

ystep = .5
canvas = area.T(x_coord = category_coord.T(sd, 0),
                x_axis=axis.X(label="Benchmarks", format="/a90%s"),
                y_axis=axis.Y(label="Memory Overhead (x)", tic_interval=ystep),
                y_grid_interval=ystep,
                y_range=(0,None),
                legend=None)

canvas.add_plot(bar_plot.T(label="mem", data=sd))

canvas.draw()
