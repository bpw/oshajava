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

prof.configPychart(name="conciseness", color=False)

statses = static.load_all(sys.argv[1:])

conciseness = []
all_size = 0
all_methods = 0
for name, stats in statses:
    groupmember = prof.distTotal(static.getGroupMemberAnns(stats))
    groupdecl = prof.distTotal(static.getGroupDeclAnns(stats))
    noncomm = prof.distTotal(static.getNonCommAnns(stats))
    inline = prof.distTotal(static.getInlineAnns(stats))
    modmember = prof.distTotal(static.getModMemberAnns(stats))
    
    methods = float(prof.distTotal(static.getMethods(stats)))
    
    conciseness.append((static.BENCH_NAMES[name],
                        groupmember / methods,
                        groupdecl / methods,
                        noncomm / methods,
                        inline / methods,
                        modmember / methods,
    ))
    
    all_size += groupmember + groupdecl + noncomm + inline + modmember
    all_methods += methods

print 'Conciseness:', conciseness
print 'Overall:', float(all_size) / all_methods

ystep = 0.2
canvas = pychart.area.T(x_coord = pychart.category_coord.T(conciseness, 0),
                        x_axis = pychart.axis.X(label="Benchmarks",
                                                format="/a90%s"),
                        y_axis = pychart.axis.Y(label="Annotations per method",
                                                tic_interval=ystep),
                        y_grid_interval = ystep)
groupmember_t = pychart.bar_plot.T(label="Group membership", data=conciseness)                        
groupdecl_t = pychart.bar_plot.T(label="Group declaration", data=conciseness,
                                        hcol=2, stack_on=groupmember_t)
noncomm_t = pychart.bar_plot.T(label="Non-communicator", data=conciseness,
                                        hcol=3, stack_on=groupdecl_t)
inline_t = pychart.bar_plot.T(label="Inline", data=conciseness,
                                        hcol=4, stack_on=noncomm_t)
modmember_t = pychart.bar_plot.T(label="Module membership", data=conciseness,
                                        hcol=5, stack_on=inline_t)
canvas.add_plot(groupmember_t, groupdecl_t, noncomm_t, inline_t, modmember_t)
canvas.draw()
