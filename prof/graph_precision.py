#!/usr/bin/env python

import prof
import sys

import pychart
if not hasattr(pychart, 'area'):
    import pychart.area
    import pychart.bar_plot
    import pychart.axis

prof.configPychart(name="precision", color=False)

nthreads = 8

options = {
    "profile" : "true", 
    "traces" : "false", 
    "record" : "true", 
}

def jgfName(name):
    # remove "JGF" from head, "BenchSize_" from end
    return name if not name.startswith("JGF") else name[3:-10]

profs = prof.loadAll(sys.argv[1:], 
                     filename_filter=(lambda fn: not fn.endswith("warmup.py")),
                     prof_filter=(lambda p: prof.matchOptions(options, p) and
                                            p['threads'] in (4,7)))

precision = []
for p in profs:
    name = jgfName(p["mainClass"])
    if prof.getSpecNodes(p) == 0:
        node_prec = 0
    else:
        node_prec = float(prof.getRunNodes(p)) / prof.getSpecNodes(p) * 100
    if prof.getSpecNodes(p) == 0:
        node_prec = 0
    else:
        edge_prec = float(prof.getRunEdges(p)) / prof.getSpecEdges(p) * 100
    precision.append((name, node_prec, edge_prec))
print 'Node and edge precision:', precision

ystep = 20
canvas = pychart.area.T(x_coord = pychart.category_coord.T(precision, 0),
                        x_axis = pychart.axis.X(label="Benchmarks",
                                                format="/a90%s"),
                        y_axis = pychart.axis.Y(label="Coverage",
                                                tic_interval=ystep,
                                                format="%i%%"),
                        y_grid_interval = ystep)
node_t = pychart.bar_plot.T(label="Nodes", data=precision, cluster=(0,2))                        
edge_t = pychart.bar_plot.T(label="Edges", data=precision, cluster=(1,2),
                            hcol=2)
canvas.add_plot(node_t, edge_t)
canvas.draw()
