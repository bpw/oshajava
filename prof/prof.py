import os

def load(file):
    f = open(file, 'r')
    prof = eval(f.read())
    f.close()
    if "mainClass" not in prof["options"]:
        prof["options"]["mainClass"] = os.path.basename(file).split("-")[0]
    prof["mainClass"] = prof["options"]["mainClass"]
    return prof

def loadAll(files, filename_filter=(lambda x: True), prof_filter=(lambda x: True)):
    return filter(prof_filter, map(load, filter(lambda f: filename_filter(os.path.basename(f)), files)))

def toTuple(fields, profile):
    return tuple([ profile[f] for f in fields ])

def mapToTuples(fields, profiles):
    return map(lambda p: toTuple(fields, p), profiles)

def mapToField(field, profiles):
    return map(lambda p: p[field], profiles)

def matchOptions(options, profile):
    for k,v in options.iteritems():
        if profile["options"][k] != v:
            return False
    return True

def splitByConfig(profiles):
    configs = {}
    for p in profiles:
        key = p["options"]
        if key in configs:
            configs[key].append(p)
        else:
            configs[key] = [p]
    return configs

def bisect(filt, list):
    y,n = [],[]
    for i in list:
        if filt(i):
            y.append(i)
        else:
            n.append(i)
    return (y,n)

def partition(mapper, list):
    parts = {}
    for i in list:
        key = mapper(i)
        if key in parts:
            parts[key].append(i)
        else:
            parts[key] = [i]
    return sorted(parts.items())

def distAverage(*dists, **kwargs):
    drop_zero = ('drop_zero' in kwargs)
    total = 0
    count = 0
    for dist in dists:
        for t, c in dist.items():
            if drop_zero and t == 0:
                continue
            total += t*c
            count += c
    if not count:
        return 0
    return float(total) / count

def distTotal(*dists):
    total = 0
    for dist in dists:
        for t, c in dist.items():
            total += t*c
    return total


## accessors
def getRuntime(prof):
    return prof["Premain to fini time"]

def getInstime(prof):
    return prof["Instrumentation time"]

def getPeakMem(prof):
    return prof["Memory peak"]

## profile accessors
def getReads(prof):
    return prof["All field reads"] + prof["All array reads"]

def getCommReads(prof):
    return prof["Communicating field reads"] + prof["Communicating array reads"]

def getChecks(prof):
    return prof["All field reads"] + prof["All array reads"] + prof["All acquires"]

def getStackWalks(prof):
    return prof["Full stack walks"]

def getSlowComms(prof):
    return prof["Communicating field read slow path"] +  prof["Communicating array read slow path"] +  prof["Communicating acquire slow path"]

def getSlowMemoHits(prof):
    return getSlowComms(prof) - getStackWalks(prof)

def getComms(prof):
    return prof["Communicating field reads"] +  prof["Communicating array reads"] +  prof["Communicating acquires"]

def getFastMemoHits(prof):
    return getComms(prof) - getSlowComms(prof)

def getThreadLocalHits(prof):
    return getChecks(prof) - getComms(prof)

def getThreadLocalRate(prof):
    return float(getThreadLocalHits(prof)) / float(getChecks(prof))

def getCommRate(prof):
    return float(getComms(prof)) / float(getChecks(prof))

def getFastMemoHitRate(prof):
    return flaot(getFastMemoHits(prof)) / float(getChecks(prof))

def getSlowMemoHitRate(prof):
    return float(getSlowMemoHits(prof)) / float(getChecks(prof))

def getStackWalkRate(prof):
    return float(getStackWalks(prof)) / float(getChecks(prof))

def getStackDepthDist(prof):
    return prof['Communicating stack depths']

def getStackSegmentLengthDist(prof):
    return prof['Length in methods of stack segments']

def getStackSegmentCountDist(prof): # (half)
    return prof['Segments on a communicating stack']

def getCommunicatingModules(prof):
    return prof['Modules used']

def getSpecNodes(prof):
    return prof['Total comm nodes in used specs']

def getSpecEdges(prof):
    return prof['Total comm edges in used specs']

def getRunNodes(prof):
    return prof['Total comm nodes in run']

def getRunEdges(prof):
    return prof['Total comm edges in run']


######### Graph config ########

def configPychart(name=None,color=True):
    assert name != None
    import pychart
    if not hasattr(pychart, 'theme'):
        # Required by my version of pychart --ALDS
        import pychart.theme
    pychart.theme.get_options()
    pychart.theme.use_color = color
    pychart.theme.output_file = name + ".pdf"
    pychart.theme.reinitialize()
