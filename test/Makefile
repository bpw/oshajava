run: clean compile
	oshajava -quiet -record -errorAction=NONE -- BoundedBufferMain

compile: BoundedBuffer.java BoundedBufferMain.java
	oshajavac -Aoshajava.annotation.default=noncomm BoundedBuffer.java
	oshajavac -Aoshajava.annotation.default=noncomm BoundedBufferMain.java


clean:
	rm *.{class,py,xml,om{m,s,i}}; oshamodrm

.phony: run clean
