run: clean compile
	oshajava -- test.run.bb.BoundedBufferMain

compile: test/run/bb/BoundedBuffer.java test/run/bb/BoundedBufferMain.java
	oshajavac test/run/bb/*.java


clean:
	rm *.{class,py,xml,om{m,s,i}}; oshamodrm

.phony: run clean
