#!/usr/bin/env python

from pychart import *
import prof
import sys

namekey = "checks"
prof.configPychart(name=namekey, color=False)

options = {
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

def getName(p):
    if p["mainClass"].startswith("JGF"):
        # remove "JGF" from head, "BenchSize_" from end
        return p["mainClass"][3:-10].capitalize()
    elif p["mainClass"] == "Harness":
        return p["options"]["profileExt"].split('-')[1].capitalize()


all = prof.loadAll(sys.argv[1:], 
                   filename_filter=(lambda fn: not fn.endswith("warmup.py")),
                   prof_filter=(lambda p: prof.matchOptions(options, p)))

def checks((name, profiles)):
    array, index = prof.bisect(lambda p: prof.matchOptions({"arrayIndexStates" : "false"}, p), profiles)
    atotal = float(sum(map(prof.getChecks, array)))
    itotal = float(sum(map(prof.getChecks, index)))
    return (name, 
            float(sum(map(prof.getThreadLocalHits, array))) / atotal,
            float(sum(map(prof.getFastMemoHits, array))) / atotal,
            float(sum(map(prof.getSlowMemoHits, array))) / atotal,
            float(sum(map(prof.getStackWalks, array))) / atotal,
            float(sum(map(prof.getThreadLocalHits, index))) / itotal,
            float(sum(map(prof.getFastMemoHits, index))) / itotal,
            float(sum(map(prof.getSlowMemoHits, index))) / itotal,
            float(sum(map(prof.getStackWalks, index))) / itotal)
            

jgf,dacapo = prof.bisect(lambda p: p["mainClass"].startswith("JGF"), all)


######### JGF
jgfdata = map(checks, prof.partition(getName, jgf))

print 'Per program:', jgfdata
ystep = .2
canvas = area.T(x_coord = category_coord.T(jgfdata, 0),
                x_axis=axis.X(label="Java Grande", format="/a90%s"),
                y_axis=axis.Y(label="Fraction of Reads", tic_interval=ystep),
                y_grid_interval=float(ystep) / 2.0,
                y_range=(0,1),
                size=(160,110),
                legend=legend.T(loc=(230,3)))


tl = bar_plot.T(label="Array: Thread-local", data=jgfdata, cluster=(0,2), fill_style=fill_style.black)
fast = bar_plot.T(label="Array: Fast memo",  data=jgfdata, cluster=(0,2), hcol=2, stack_on=tl, fill_style=fill_style.white)
# slow = bar_plot.T(label="Slow memo - array",  data=jgfdata, cluster=(0,2), hcol=3, stack_on=fast)
# walk = bar_plot.T(label="Stack walk - array", data=jgfdata, cluster=(0,2), hcol=4, stack_on=slow)

itl = bar_plot.T(label="Element: Thread-local", data=jgfdata, cluster=(1,2), hcol=5, fill_style=fill_style.gray50)
ifast = bar_plot.T(label="Element: Fast memo",  data=jgfdata, cluster=(1,2), hcol=6, stack_on=itl, fill_style=fill_style.gray70)
# islow = bar_plot.T(label="Slow memo - index",  data=jgfdata, cluster=(1,2), hcol=7, stack_on=ifast)
# iwalk = bar_plot.T(label="Stack walk - index", data=jgfdata, cluster=(1,2), hcol=8, stack_on=islow)

# canvas.add_plot(tl, fast, slow, walk, itl, ifast, islow, iwalk)
canvas.add_plot(tl, fast, itl, ifast)

canvas.draw()

c = checks(('all', all))

print 'Array across all:', c[1], 'thread-local,', c[2], 'fast memo,', c[3], 'slow memo,', c[4], 'stack walk'
print 'Element across all:', c[5], 'thread-local,', c[6], 'fast memo,', c[7], 'slow memo,', c[8], 'stack walk'

for b in jgfdata:
    print b[0], ' & ', ' & '.join(map(lambda x: '%(x)4g' % {'x':100.0*x}, b[1:])), '\\\\'



########### DACAPO
dacapodata = map(checks, prof.partition(getName, dacapo))

print 'Per program:', dacapodata
ystep = .2
canvas = area.T(x_coord = category_coord.T(dacapodata, 0),
                x_axis=axis.X(label="DaCapo", format="/a90%s"),
                y_axis=None, #axis.Y(label="Fraction of Reads", tic_interval=ystep),
                y_grid_interval=float(ystep) / 2.0,
                y_range=(0,1),
                size=(60,110),
                loc=(160,0),
                legend=None)

tl = bar_plot.T(label="Array: Thread-local", data=dacapodata, cluster=(0,2), fill_style=fill_style.black)
fast = bar_plot.T(label="Array: Fast memo",  data=dacapodata, cluster=(0,2), hcol=2, stack_on=tl, fill_style=fill_style.white)
# slow = bar_plot.T(label="Slow memo - array",  data=dacapodata, cluster=(0,2), hcol=3, stack_on=fast)
# walk = bar_plot.T(label="Stack walk - array", data=dacapodata, cluster=(0,2), hcol=4, stack_on=slow)

itl = bar_plot.T(label="Element: Thread-local", data=dacapodata, cluster=(1,2), hcol=5, fill_style=fill_style.gray50)
ifast = bar_plot.T(label="Element: Fast memo",  data=dacapodata, cluster=(1,2), hcol=6, stack_on=itl, fill_style=fill_style.gray70)
# islow = bar_plot.T(label="Slow memo - index",  data=dacapodata, cluster=(1,2), hcol=7, stack_on=ifast)
# iwalk = bar_plot.T(label="Stack walk - index", data=dacapodata, cluster=(1,2), hcol=8, stack_on=islow)

# canvas.add_plot(tl, fast, slow, walk, itl, ifast, islow, iwalk)
canvas.add_plot(tl, fast, itl, ifast)

canvas.draw()

c = checks(('all', all))

print 'Array across all:', c[1], 'thread-local,', c[2], 'fast memo,', c[3], 'slow memo,', c[4], 'stack walk'
print 'Element across all:', c[5], 'thread-local,', c[6], 'fast memo,', c[7], 'slow memo,', c[8], 'stack walk'

for b in dacapodata:
    print b[0], ' & ', ' & '.join(map(lambda x: '%(x)4g' % {'x':100.0*x}, b[1:])), '\\\\'
