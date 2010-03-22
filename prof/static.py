import os

def load_all(paths):
    statses = []
    for path in paths:
        bmname, _ = os.path.splitext(os.path.basename(path))
        statses.append((bmname, eval(open(path).read())))
    return statses

## accessors
def getMethods(stats):
    return stats['Total methods']

def getInlined(stats):
    return stats['Inlined methods']

def getCGroups(stats):
    return stats['Communication groups']

def getIGroups(stats):
    return stats['Interface groups']

def getMemberships(stats):
    return stats['Total group memberships']

def getAnnotations(stats):
    return stats['Source annotations']
