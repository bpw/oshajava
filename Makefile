BIN=bin
SRC=src
ASM=asm-3.2/lib/asm-3.2.jar
CLASSPATH=$(ASM):$(BIN)

JAVAC=javac

MAIN=oshaj/Instrumentor.java

build:	$(SRC)/$(MAIN)
	$(JAVAC) -d $(BIN) -classpath $(CLASSPATH) -sourcepath $(SRC) $(MAIN)

clean:
	rm -r bin/*

