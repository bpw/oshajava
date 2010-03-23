#!/usr/bin/env python

from pychart import *
import prof
import sys

prof.configPychart(name="mem", color=False)

nthreads = 8

options = {
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

all =  prof.loadAll(sys.argv[1:], 
                    filename_filter=(lambda fn: not fn.endswith("warmup.py")),
                    prof_filter=(lambda p: prof.matchOptions(options, p) and p["options"]["profileExt"].startswith("-8-threads")))

def overhead((name, profiles)):
    oshajava, java = prof.bisect(lambda p: prof.matchOptions({"noInstrument" : "false"}, p), profiles)
    array,index  = prof.bisect(lambda p: prof.matchOptions({"arrayIndexStates" : "false"}, p), oshajava)
    ojasum = float(sum(map(prof.getPeakMem, array)))
    ojisum = float(sum(map(prof.getPeakMem, index)))
    jsum = float(sum(map(prof.getPeakMem, java)))
    return (name, ojasum / jsum, ojisum / jsum)

sd = sorted(map(overhead, prof.partition(lambda p: jgfName(p["mainClass"]), all)))

print sd

ystep = 1
y_max = 6
canvas = area.T(x_coord = category_coord.T(sd, 0),
                x_axis=axis.X(label="Benchmarks", format="/a90%s"),
                y_axis=axis.Y(label="Memory Overhead (x)", tic_interval=ystep),
                y_grid_interval=float(ystep) / 2.0,
                y_range=(0,y_max))

canvas.add_plot(bar_plot.T(label="Array", data=sd, cluster=(0,2)))
canvas.add_plot(bar_plot.T(label="Element", data=sd, cluster=(1,2), hcol=2))

canvas.draw()
