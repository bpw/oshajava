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

prof.configPychart(name="mem", color=False)

statses = static.load_all(sys.argv[1:])

print 'Inlining (non-inlined / total):'
for name, stats in statses:
    inlined = prof.distTotal(static.getInlined(stats))
    total = prof.distTotal(static.getMethods(stats))
    print '  %s: %i / %i' % (JGF_NAMES[name], total-inlined, total)

print 'Annotation size:'
for name, stats in statses:
    num = prof.distTotal(static.getAnnotations(stats))
    print '  %s: %i' % (JGF_NAMES[name], num)