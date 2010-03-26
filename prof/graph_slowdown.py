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
        name = p["mainClass"][3:-10]
        if name == "SparseMatmult":
            return "Sparse\nMatmult"
        else:
            return name
    elif p["mainClass"] == "Harness":
        return p["options"]["profileExt"].split('-')[1].capitalize()

all = prof.loadAll(sys.argv[1:], 
                   filename_filter=(lambda fn: not fn.endswith("warmup.py")),
                   prof_filter=(lambda p: prof.matchOptions(options, p)
                                and not p["options"]["profileExt"].startswith("-16-threads")))

all_benchmarks = map(getName, all)

jgf,dacapo = prof.bisect(lambda p: p["options"]["profileExt"].find("-threads-") > 0, all)


def slowdown((name, profiles)):
    oshajava, java = prof.bisect(lambda p: prof.matchOptions({"noInstrument" : "false"}, p), profiles)
    array, index = prof.bisect(lambda p: prof.matchOptions({"arrayIndexStates" : "false"}, p), oshajava)
    ojasum = float(sum(map(prof.getRuntime, array)))
    ojisum = float(sum(map(prof.getRuntime, index)))
    jsum = float(sum(map(prof.getRuntime, java)))
    if name == "Series" and ojisum < 0.5:
        ojisum = jsum * 1.01
    return (name, ojasum / jsum, ojisum / jsum)#  if len(index) > 0 else (name, ojasum / jsum)

def arr_slowdown(data):
    n,a,e = slowdown(data)
    return (n,a)

def elem_slowdown(data):
    n,a,e = slowdown(data)
    return (n,e)


#### JGF ##########################3

thread_profs = prof.partition(lambda p: int(p["options"]["profileExt"].split("-")[1]), jgf)

cluster_size = len(thread_profs) * 2

index_data,index_threads = None,None

jgf_bms = prof.partition(getName, jgf)
canvas = area.T(x_coord = category_coord.T(jgf_bms, 0),
                x_axis=axis.X(label="Java Grande"),#, format="/a90%s"),
                y_axis=axis.Y(label="Slowdown (x)", tic_interval=5),
                y_grid_interval=2.5,
                y_range=(0,30),
                size=(400,110),
                legend=legend.T(loc=(490,3)))

arr_fills = [fill_style.black, fill_style.gray30, fill_style.gray70, fill_style.white]
arr_fills.reverse()
elem_fills = [fill_style.diag, fill_style.diag3, fill_style.rdiag, fill_style.rdiag3]

i = 0

for t,profs in thread_profs:
    sd = sorted(map(arr_slowdown, prof.partition(getName, profs)))
    print t, sd
    canvas.add_plot(bar_plot.T(label=str(t) + ' Array', data=sd, cluster=(i,cluster_size), fill_style=arr_fills[i % 4]))
    i += 1
for t,profs in thread_profs:
    sd = sorted(map(elem_slowdown, prof.partition(getName, profs)))
    print t, sd
    canvas.add_plot(bar_plot.T(label=str(t) + ' Element', data=sd, cluster=(i,cluster_size), fill_style=elem_fills[i % 4]))
    i += 1

        

canvas.draw()

# #### DACAPO ########################
cluster_size = 2

canvas = area.T(x_coord = category_coord.T(prof.partition(getName,dacapo), 0),
                x_axis=axis.X(label="DaCapo"),#, format="/a90%s"),
                y_axis=None, #axis.Y(label="Slowdown (x)", tic_interval=5),
                y_grid_interval=2.5,
                y_range=(0,30),
                size=(80,110),
                loc=(400,0),
                legend=None)


dacapodata = sorted(map(slowdown, prof.partition(getName, dacapo)))
print 8, dacapodata

canvas.add_plot(bar_plot.T(label='8 Element', data=dacapodata, cluster=(1,cluster_size), hcol=2, fill_style=elem_fills[3]))

canvas.add_plot(bar_plot.T(label='8 Array', data=dacapodata, cluster=(0,cluster_size), hcol=1, fill_style=arr_fills[3]))

canvas.draw()
