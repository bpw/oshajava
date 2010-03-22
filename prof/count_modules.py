#!/usr/bin/env python

import prof
import static

import sys
import os

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

statses = static.load_all(sys.argv[1:])

out = []
all_methods = 0
all_modules = 0
for name, stats in statses:
    mdist = static.getMethods(stats)
    modules = sum(mdist.values())
    avgmethods = prof.distAverage(mdist)
    out.append((JGF_NAMES[name], modules, avgmethods))
    all_methods += prof.distTotal(mdist)
    all_modules += modules
print 'Num modules, average methods per module:', out
print 'Overall methods per module:', float(all_methods) / all_modules
