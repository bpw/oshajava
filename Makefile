BIN=bin
SRC=src
ASMDIR=asm-3.2/lib
ASMJAR=asm-3.2.jar
CLASSPATH=$(ASMDIR)/$(ASMJAR):$(BIN)
CLASSLIST=$(BIN)/classlist.txt
OSHAJAR=oshaj.jar
MANIFEST=$(SRC)/Manifest.txt

JAVAC=javac

MAIN=$(SRC)/oshaj/Instrumentor.java

build:	classes classlist
	jar cfm $(OSHAJAR) $(MANIFEST) @$(CLASSLIST) -C $(ASMDIR) $(ASMJAR)
	
classlist:	classes
	find $(BIN) -name '*.class' | sed -e 's+$(BIN)/+-C $(BIN) +' > $(CLASSLIST)

classes:	$(MAIN)
	$(JAVAC) -d $(BIN) -classpath $(CLASSPATH) -sourcepath $(SRC) $(MAIN)

clean:
	rm -rf $(BIN)/*

distclean:
	rm -f $(OSHAJAR)

fullclean:	clean distclean cleantests

### Test stuff. ###

buildtests:
	cd test && $(JAVAC) *.java
	
runtests:	buildtests build
	cd test && find . -name '*.class' | sed -e 's+^./++' -e 's/.class//' | xargs java -javaagent:../$(DIST)/$(OSHAJAR) 

cleantests:
	rm -f test/*.class
	