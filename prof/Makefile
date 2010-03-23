ALL_FILES = jgf/* jgf-arrayIndexStates/* dacapo-new/*.py


all: perf

perf:
	./graph_slowdown.py $(ALL_FILES)
	./graph_mem_overhead.py $(ALL_FILES)
	./graph_checks.py $(ALL_FILES)
