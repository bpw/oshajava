#!/usr/bin/env python

import prof
import static

import sys
import os

import pychart
if not hasattr(pychart, 'area'):
    import pychart.area
    import pychart.bar_plot
    import pychart.axis

JGF_NAMES = {
    'crypt': 'Crypt',
    'lufact': 'LUFact',
    'moldyn': 'MolDyn',
    'montecarlo': 'MonteCarlo',
    'raytracer': 'RayTracer',
    'sor': 'SOR',
    'series': 'Series',
    'sparsematmult': 'SparseMatmult',
}

prof.configPychart(name="inlining", color=False)

statses = static.load_all(sys.argv[1:])

inlining = []
all_inlined = 0
all_total = 0
for name, stats in statses:
    inlined = prof.distTotal(static.getInlined(stats))
    total = prof.distTotal(static.getMethods(stats))
    inlining.append((JGF_NAMES[name], inlined, total-inlined))
    all_inlined += inlined
    all_total += total
print 'Inlining:', inlining
print 'Overall: %f%%' % (float(all_inlined) / all_total * 100)

ystep = 50
canvas = pychart.area.T(x_coord = pychart.category_coord.T(inlining, 0),
                        x_axis = pychart.axis.X(label="Benchmarks",
                                                format="/a90%s"),
                        y_axis = pychart.axis.Y(label="Methods",
                                                tic_interval=ystep),
                        y_grid_interval = ystep)
inlined_t = pychart.bar_plot.T(label="Inlined", data=inlining)                        
noninlined_t = pychart.bar_plot.T(label="Not inlined", data=inlining,
                                  hcol=2, stack_on=inlined_t)
canvas.add_plot(inlined_t, noninlined_t)
canvas.draw()
