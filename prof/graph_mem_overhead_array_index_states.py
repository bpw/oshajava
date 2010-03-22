#!/usr/bin/env python

from pychart import *
import prof
import sys

prof.configPychart(name="mem-array-index-states", color=False)

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

def slowdown((name, profiles)):
    java = filter(lambda p: prof.matchOptions({"noInstrument" : "false"}, p), profiles)
    oshajava = filter(lambda p: prof.matchOptions({"arrayIndexStates" : "true", "noInstrument" : "false"}, p), profiles)
    ojtimes = map(prof.getPeakMem, oshajava)
    jtimes = map(prof.getPeakMem, java)
    return (name, float(sum(ojtimes)) / float(sum(jtimes)))

sd = sorted(map(slowdown, prof.partition(lambda p: jgfName(p["mainClass"]), all)))

canvas = area.T(x_coord = category_coord.T(sd, 0),
                x_axis=axis.X(label="Benchmarks", format="/a90%s"),
                y_axis=axis.Y(label="Memory Overhead (x)", tic_interval=5),
                y_grid_interval=5)

canvas.add_plot(bar_plot.T(label="8 threads", data=sd))

canvas.draw()
