import os

BENCH_NAMES = {
    'crypt': 'Crypt',
    'lufact': 'LUFact',
    'moldyn': 'MolDyn',
    'montecarlo': 'MonteCarlo',
    'raytracer': 'RayTracer',
    'sor': 'SOR',
    'series': 'Series',
    'sparsematmult': 'SparseMatmult',
    'avrora': 'Avrora',
    'xalan': 'Xalan',
    'batik': 'Batik',
}

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

def getGroupMemberAnns(stats):
    return stats['Group membership annotations']

def getGroupDeclAnns(stats):
    return stats['Group declaration annotations']

def getNonCommAnns(stats):
    return stats['Non-communicator annotations']
    
def getInlineAnns(stats):
    return stats['Inline annotations']
    
def getModMemberAnns(stats):
    return stats['Module membership annotations']