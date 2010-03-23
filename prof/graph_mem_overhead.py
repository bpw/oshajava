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

def getName(p):
    if p["mainClass"].startswith("JGF"):
        # remove "JGF" from head, "BenchSize_" from end
        return p["mainClass"][3:-10].capitalize()
    elif p["mainClass"] == "Harness":
        return p["options"]["profileExt"].split('-')[1].capitalize()

all =  prof.loadAll(sys.argv[1:], 
                    filename_filter=(lambda fn: not fn.endswith("warmup.py")),
                    prof_filter=(lambda p: prof.matchOptions(options, p) and (p["options"]["profileExt"].startswith("-8-threads") or p["mainClass"] == "Harness")))

def overhead((name, profiles)):
    oshajava, java = prof.bisect(lambda p: prof.matchOptions({"noInstrument" : "false"}, p), profiles)
    array,index  = prof.bisect(lambda p: prof.matchOptions({"arrayIndexStates" : "false"}, p), oshajava)
    ojasum = float(sum(map(prof.getPeakMem, array)))
    ojisum = float(sum(map(prof.getPeakMem, index)))
    jsum = float(sum(map(prof.getPeakMem, java)))
    return (name, ojasum / jsum, ojisum / jsum)

jgf,dacapo = prof.bisect(lambda p: p["mainClass"].startswith("JGF"), all)


#### JGF #########
jgfdata = map(overhead, prof.partition(getName, jgf))

print jgfdata

ystep = 2
y_max = 11
canvas = area.T(x_coord = category_coord.T(jgfdata, 0),
                x_axis=axis.X(label="Java Grande", format="/a90%s"),
                y_axis=axis.Y(label="Memory Overhead (x)", tic_interval=ystep),
                y_grid_interval=float(ystep) / 2.0,
                y_range=(0,y_max),
                size=(140,110),
                legend=legend.T(loc=(200,3)))

canvas.add_plot(bar_plot.T(label="Array", data=jgfdata, cluster=(0,2), fill_style=fill_style.black))
canvas.add_plot(bar_plot.T(label="Element", data=jgfdata, cluster=(1,2), hcol=2, fill_style=fill_style.gray70))

def assoc1(key, list):
    for t in list:
        if t[0] == key:
            return t
    return None
crypt_elem_overhead = str(assoc1('Crypt', jgfdata)[2])[:4]

text_box.T(loc=(3,112), text='{/6(' + str(crypt_elem_overhead) + ')}', line_style=None).draw()
canvas.draw()

#### DACAPO ########
dacapodata = map(overhead, prof.partition(getName, dacapo))

print dacapodata

canvas = area.T(x_coord = category_coord.T(dacapodata, 0),
                x_axis=axis.X(label="DaCapo", format="/a90%s"),
                y_axis=None, #axis.Y(label="Memory Overhead (x)", tic_interval=ystep),
                y_grid_interval=float(ystep) / 2.0,
                y_range=(0,y_max),
                size=(50,110),
                loc=(140,0),
                legend=None)

canvas.add_plot(bar_plot.T(label="Array", data=dacapodata, cluster=(0,2), fill_style=fill_style.black))
canvas.add_plot(bar_plot.T(label="Element", data=dacapodata, cluster=(1,2), hcol=2, fill_style=fill_style.gray70))

canvas.draw()

##################
