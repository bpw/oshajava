#!/usr/bin/env python

import prof
import sys

import pychart
if not hasattr(pychart, 'area'):
    import pychart.area
    import pychart.bar_plot
    import pychart.axis

prof.configPychart(name="segsize", color=False)

nthreads = 8

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

profs = prof.loadAll(sys.argv[1:], 
                     filename_filter=(lambda fn: not fn.endswith("warmup.py")),
                     prof_filter=(lambda p: prof.matchOptions(options, p)))
bench_profs = prof.partition(lambda p: jgfName(p["mainClass"]), profs)

segsizes = []
for name, bp in bench_profs:
    size = prof.distAverage(*[prof.getStackSegmentLengthDist(p) for p in bp])
    segsizes.append((name, size))
print 'Mean segment size:', segsizes

ystep = 0.5
canvas = pychart.area.T(x_coord = pychart.category_coord.T(segsizes, 0),
                        x_axis = pychart.axis.X(label="Benchmarks",
                                                format="/a90%s"),
                        y_axis = pychart.axis.Y(label="Methods",
                                                tic_interval=ystep),
                        y_grid_interval = ystep,
                        legend = None)
segsize_t = pychart.bar_plot.T(label="Mean stack segment size", data=segsizes)
canvas.add_plot(segsize_t)
canvas.draw()

# print 'Stack depth (in methods / in segments):'
# for name, bp in bench_profs:
#     methods = prof.distAverage(*[prof.getStackDepthDist(p) for p in bp])
#     segments = prof.distAverage(*[prof.getStackSegmentCountDist(p) for p in bp])
#     print '  %s: %f / %f' % (name, methods, segments)
