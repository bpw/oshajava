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
    import pychart.fill_style

DACAPO = ('avrora', 'batik', 'xalan')

prof.configPychart(name="conciseness", color=False)

statses = static.load_all(sys.argv[1:])

jgf_conciseness = []
dc_conciseness = []
all_size = 0
all_methods = 0
for name, stats in statses:
    groupmember = prof.distTotal(static.getGroupMemberAnns(stats))
    groupdecl = prof.distTotal(static.getGroupDeclAnns(stats))
    noncomm = prof.distTotal(static.getNonCommAnns(stats))
    inline = prof.distTotal(static.getInlineAnns(stats))
    modmember = prof.distTotal(static.getModMemberAnns(stats))
    
    methods = float(prof.distTotal(static.getMethods(stats)))
    
    vals = (
        static.BENCH_NAMES[name],
        groupmember / methods,
        groupdecl / methods,
        noncomm / methods,
        inline / methods,
        modmember / methods,
    )
    if name in DACAPO:
        dc_conciseness.append(vals)
    else:
        jgf_conciseness.append(vals)
    
    all_size += groupmember + groupdecl + noncomm + inline + modmember
    all_methods += methods

print 'Conciseness:', jgf_conciseness + dc_conciseness
print 'Overall:', float(all_size) / all_methods

def doplot(conciseness, ystep, offset, legend, width, bmlabel, ylabel):
    args = {
        'x_coord': pychart.category_coord.T(conciseness, 0),
        'x_axis': pychart.axis.X(label=bmlabel,
                               format="/a90%s"),
        'y_axis': pychart.axis.Y(label=ylabel,
                               tic_interval=ystep),
        'y_grid_interval': ystep,
        'loc': (offset, 0),
    }
    if not legend:
        args['legend'] = None
    if width:
        args['size'] = (width, 110)
    canvas = pychart.area.T(**args)
    groupmember_t = pychart.bar_plot.T(label="Group membership", data=conciseness,
                                       fill_style=pychart.fill_style.black)                        
    groupdecl_t = pychart.bar_plot.T(label="Group declaration", data=conciseness,
                                            hcol=2, stack_on=groupmember_t,
                                       fill_style=pychart.fill_style.gray70)
    noncomm_t = pychart.bar_plot.T(label="Non-communicator", data=conciseness,
                                            hcol=3, stack_on=groupdecl_t,
                                       fill_style=pychart.fill_style.diag)
    inline_t = pychart.bar_plot.T(label="Inline", data=conciseness,
                                            hcol=4, stack_on=noncomm_t,
                                       fill_style=pychart.fill_style.diag2)
    modmember_t = pychart.bar_plot.T(label="Module membership", data=conciseness,
                                            hcol=5, stack_on=inline_t,
                                       fill_style=pychart.fill_style.white)
    canvas.add_plot(groupmember_t, groupdecl_t, noncomm_t, inline_t, modmember_t)
    canvas.draw()

doplot(jgf_conciseness, 0.2, 0, False, None, "Java Grande benchmarks", "Annotations per method")
doplot(dc_conciseness, 0.005, 155, True, 60, "DaCapo benchmarks", None)

