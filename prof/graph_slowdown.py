#!/usr/bin/env python

from pychart import *
import prof
import sys

prof.configPychart(name="slowdown", color=False)

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

thread_profs = prof.partition(lambda p: int(p["options"]["profileExt"].split("-")[1]),
                             prof.loadAll(sys.argv[1:], 
                                          filename_filter=(lambda fn: not fn.endswith("warmup.py")),
                                          prof_filter=(lambda p: prof.matchOptions(options, p))))

def slowdown((name, profiles)):
    oshajava, java = prof.bisect(lambda p: prof.matchOptions({"noInstrument" : "false"}, p), profiles)
    ojtimes = map(prof.getRuntime, oshajava)
    jtimes = map(prof.getRuntime, java)
    return (name, float(sum(ojtimes)) / float(sum(jtimes)))

made_canvas = False

i = 0
for t,profs in thread_profs:
    sd = sorted(map(slowdown, prof.partition(lambda p: jgfName(p["mainClass"]), profs)))
    print t, sd
    if not made_canvas:
        canvas = area.T(x_coord = category_coord.T(sd, 0),
                x_axis=axis.X(label="Benchmarks", format="/a60%s"),
                y_axis=axis.Y(label="Slowdown (x)", tic_interval=5),
                y_grid_interval=5,
                size=(240,110))
        made_canvas = True
    canvas.add_plot(bar_plot.T(label=str(t), data=sd, cluster=(i,len(thread_profs))))
    i += 1

canvas.draw()
