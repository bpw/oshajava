#!/usr/bin/env python

# SUGGESTED:
# python2.5 graph_precision.py jgf/*record* dacapo-new/*record*.py

import prof
import sys

import pychart
if not hasattr(pychart, 'area'):
    import pychart.area
    import pychart.bar_plot
    import pychart.axis
    import pychart.legend

prof.configPychart(name="precision", color=False)

nthreads = 8

options = {
    "profile" : "false", 
    "traces" : "false", 
    "record" : "true", 
}

def dcName(p):
    return p["options"]["profileExt"].split('-', 3)[1].capitalize()

def jgfName(name):
    # remove "JGF" from head, "BenchSize_" from end
    name = name if not name.startswith("JGF") else name[3:-10]
    if name == 'SOR':
        name = 'Sor' # sort order
    return name

profs = prof.loadAll(sys.argv[1:], 
                     filename_filter=(lambda fn: not fn.endswith("warmup.py")),
                     prof_filter=(lambda p: prof.matchOptions(options, p)))

dc_seen = set()
jgf_precision = []
dc_precision = []
for p in profs:
    if p["mainClass"] == 'Harness':
        # DaCapo
        name = dcName(p)
        out = dc_precision
        if name in dc_seen:
            continue
        dc_seen.add(name)
    else:
        # JGF
        name = jgfName(p["mainClass"])
        out = jgf_precision
    name = name.lower().capitalize()
        
    if prof.getSpecNodes(p) == 0:
        node_prec = 0
    else:
        node_prec = float(prof.getRunNodes(p)) / prof.getSpecNodes(p) * 100
    if prof.getSpecEdges(p) == 0:
        edge_prec = 0
    else:
        edge_prec = float(prof.getRunEdges(p)) / prof.getSpecEdges(p) * 100
    print '%s : %i/%i , %i/%i' % (name, prof.getRunNodes(p), prof.getSpecNodes(p), prof.getRunEdges(p), prof.getSpecEdges(p))
    out.append((name, node_prec, edge_prec))
print 'Node and edge precision:', jgf_precision + dc_precision

jgf_precision.sort()
dc_precision.sort()

ystep = 20

# Java Grande

width = 140
canvas = pychart.area.T(x_coord = pychart.category_coord.T(jgf_precision, 0),
                        x_axis = pychart.axis.X(label="Java Grande",
                                                format="/a90%s"),
                        y_axis = pychart.axis.Y(label="% of Specification Exercised",
                                                tic_interval=ystep,
                                                format="%i%%"),
                        y_grid_interval = ystep/2,
                        size=(width, 110), legend=None, y_range=(0,100))
node_t = pychart.bar_plot.T(label="Nodes", data=jgf_precision, cluster=(0,2),
                            fill_style=pychart.fill_style.black)                        
edge_t = pychart.bar_plot.T(label="Edges", data=jgf_precision, cluster=(1,2),
                            hcol=2, fill_style=pychart.fill_style.gray70)
canvas.add_plot(node_t, edge_t)
canvas.draw()


# DaCapo

canvas = pychart.area.T(x_coord = pychart.category_coord.T(dc_precision, 0),
                        x_axis = pychart.axis.X(label="DaCapo",
                                                format="/a90%s"),
                        y_axis = None,
                        y_grid_interval = ystep/2,
                        size=(width * 3/8, 110),
                        loc=(width, 0), y_range=(0,100),
                        legend=pychart.legend.T(loc=(width*11/8+10,3)))
node_t = pychart.bar_plot.T(label="Methods", data=dc_precision, cluster=(0,2),
                            fill_style=pychart.fill_style.black)                        
edge_t = pychart.bar_plot.T(label="Method pairs", data=dc_precision, cluster=(1,2),
                            hcol=2, fill_style=pychart.fill_style.gray70)
canvas.add_plot(node_t, edge_t)
canvas.draw()

