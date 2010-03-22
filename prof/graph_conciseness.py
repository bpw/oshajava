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

prof.configPychart(name="conciseness", color=False)

statses = static.load_all(sys.argv[1:])

conciseness = []
all_size = 0
all_methods = 0
for name, stats in statses:
    anns = prof.distTotal(static.getAnnotations(stats))
    methods = prof.distTotal(static.getMethods(stats))
    conciseness.append((JGF_NAMES[name], float(anns)/methods))
    all_size += anns
    all_methods += methods
print 'Conciseness:', conciseness
print 'Overall:', float(all_size) / all_methods

ystep = 0.5
canvas = pychart.area.T(x_coord = pychart.category_coord.T(conciseness, 0),
                        x_axis = pychart.axis.X(label="Benchmarks",
                                                format="/a90%s"),
                        y_axis = pychart.axis.Y(label="Annotations per method",
                                                tic_interval=ystep),
                        y_grid_interval = ystep)
conciseness_t = pychart.bar_plot.T(label="Conciseness", data=conciseness)
canvas.add_plot(conciseness_t)
canvas.draw()
