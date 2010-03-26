#!/usr/bin/env python

import prof
import static

import sys
import os

DACAPO = ('avrora', 'batik', 'xalan')

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
    
    total_size = groupmember + groupdecl + noncomm + inline + modmember
    all_size += total_size
    all_methods += methods
    
    print '%s : %i , %i' % (name, total_size,
                            prof.distTotal(static.getCGroups(stats)))

print 'Conciseness:', jgf_conciseness + dc_conciseness
print 'Overall:', float(all_size) / all_methods
