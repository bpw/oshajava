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

def getName(p):
    if p["mainClass"].startswith("JGF"):
        # remove "JGF" from head, "BenchSize_" from end
        return p["mainClass"][3:-10]
    elif p["mainClass"] == "Harness":
        return p["options"]["profileExt"].split('-')[1]

all = prof.loadAll(sys.argv[1:], 
                   filename_filter=(lambda fn: not fn.endswith("warmup.py")),
                   prof_filter=(lambda p: prof.matchOptions(options, p)))

all_benchmarks = map(getName, all)

jgf,dacapo = prof.bisect(lambda p: p["options"]["profileExt"].find("-threads-") > 0, all)


def slowdown((name, profiles)):
    oshajava, java = prof.bisect(lambda p: prof.matchOptions({"noInstrument" : "false"}, p), profiles)
    array, index = prof.bisect(lambda p: prof.matchOptions({"arrayIndexStates" : "false"}, p), oshajava)
    ojasum = float(sum(map(prof.getRuntime, array)))
    ojisum = float(sum(map(prof.getRuntime, index)))
    jsum = float(sum(map(prof.getRuntime, java)))
    return (name, ojasum / jsum, ojisum / jsum)  if len(index) > 0 else (name, ojasum / jsum)



#### JGF ##########################3

thread_profs = prof.partition(lambda p: int(p["options"]["profileExt"].split("-")[1]), jgf)

cluster_size = len(thread_profs) + 1

index_data,index_threads = None,None

jgf_bms = prof.partition(getName, jgf)
canvas = area.T(x_coord = category_coord.T(jgf_bms, 0),
                x_axis=axis.X(label="Java Grande", format="/a90%s"),
                y_axis=axis.Y(label="Slowdown (x)", tic_interval=5),
                y_grid_interval=2.5,
                y_range=(0,30),
                size=(320,110),
                legend=legend.T(loc=(410,3)))

i = 0
for t,profs in thread_profs:
    sd = sorted(map(slowdown, prof.partition(getName, profs)))
    print t, sd
    canvas.add_plot(bar_plot.T(label=str(t) + ' Array', data=sd, cluster=(i,cluster_size)))
    i += 1
    if len(sd[0]) == 3:
        index_data,index_threads = sd,t
        

if index_data != None:
    canvas.add_plot(bar_plot.T(label=str(index_threads) + ' Element', data=index_data, cluster=(i,cluster_size), hcol=2))

canvas.draw()

# #### DACAPO ########################
cluster_size = 2

canvas = area.T(x_coord = category_coord.T(prof.partition(getName,dacapo), 0),
                x_axis=axis.X(label="DaCapo", format="/a90%s"),
                y_axis=None, #axis.Y(label="Slowdown (x)", tic_interval=5),
                y_grid_interval=2.5,
                y_range=(0,30),
                size=(80,110),
                loc=(320,0),
                legend=None)


dacapodata = sorted(map(slowdown, prof.partition(getName, dacapo)))
print 8, dacapodata

canvas.add_plot(bar_plot.T(label='8 Array', data=dacapodata, cluster=(0,cluster_size), hcol=1, fill_style=fill_style.gray50))

canvas.add_plot(bar_plot.T(label='8 Element', data=dacapodata, cluster=(1,cluster_size), hcol=2, fill_style=fill_style.rdiag))

canvas.draw()
