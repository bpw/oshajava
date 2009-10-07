BIN=bin
SRC=src
ASM=asm-3.2/lib/asm-3.2.jar
CLASSPATH=$(ASM):$(BIN)

JAVAC=javac

MAIN=$(SRC)/oshaj/Instrumentor.java

build:	$(MAIN)
	$(JAVAC) -d $(BIN) -classpath $(CLASSPATH) -sourcepath $(SRC) $(MAIN)

clean:
	rm -r $(BIN)/*

