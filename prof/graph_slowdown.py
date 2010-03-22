#!/usr/bin/env python

from pychart import *
import prof
import sys

prof.configPychart(name="slowdown", color=False)

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

thread_profs = prof.partition(lambda p: int(p["options"]["profileExt"].split("-")[1]),
                             prof.loadAll(sys.argv[1:], 
                                          filename_filter=(lambda fn: not fn.endswith("warmup.py")),
                                          prof_filter=(lambda p: prof.matchOptions(options, p))))

def slowdown((name, profiles)):
    oshajava, java = prof.bisect(lambda p: prof.matchOptions({"noInstrument" : "false"}, p), profiles)
    array, index = prof.bisect(lambda p: prof.matchOptions({"arrayIndexStates" : "false"}, p), oshajava)
    ojasum = float(sum(map(prof.getRuntime, array)))
    ojisum = float(sum(map(prof.getRuntime, index)))
    jsum = float(sum(map(prof.getRuntime, java)))
    return (name, ojasum / jsum, ojisum / jsum)  if len(index) > 0 else (name, ojasum / jsum)

canvas = None

cluster_size = len(thread_profs) + 1

index_data,index_threads = None,None

i = 0
for t,profs in thread_profs:
    sd = sorted(map(slowdown, prof.partition(lambda p: jgfName(p["mainClass"]), profs)))
    print t, sd
    if canvas == None:
        canvas = area.T(x_coord = category_coord.T(sd, 0),
                        x_axis=axis.X(label="Benchmarks", format="/a60%s"),
                        y_axis=axis.Y(label="Slowdown (x)", tic_interval=5),
                        y_grid_interval=2.5,
                        y_range=(0,None),
                        size=(300,110))
    canvas.add_plot(bar_plot.T(label=str(t) + ' Array', data=sd, cluster=(i,cluster_size)))
    i += 1
    if len(sd[0]) == 3:
        index_data,index_threads = sd,t
        
if index_data != None:
    canvas.add_plot(bar_plot.T(label=str(index_threads) + ' Index', data=index_data, cluster=(i,cluster_size), hcol=2))
canvas.draw()
