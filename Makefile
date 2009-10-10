BIN=bin
SRC=src
LIBS=libs
ASMDIR=$(LIBS)/asm-3.2/lib
ASMJAR=asm-3.2.jar
ASMCOMMONSJAR=asm-commons-3.2.jar
ACMEJAR=acme-10-7-09.jar
CLASSPATH=$(ASMDIR)/$(ASMJAR):$(ASMDIR)/$(ASMCOMMONSJAR):$(LIBS)/$(ACMEJAR):$(BIN)
CLASSLIST=$(BIN)/classlist.txt
OSHAJAR=oshaj.jar
MANIFEST=$(SRC)/Manifest.txt

JAVAC=javac

MAIN=$(SRC)/oshaj/instrument/Instrumentor.java

build:	classes classlist
	jar cfm $(OSHAJAR) $(MANIFEST) @$(CLASSLIST) -C $(ASMDIR) $(ASMJAR) -C $(ASMDIR) $(ASMCOMMONSJAR) -C $(LIBS) $(ACMEJAR)
	
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
	cd test && find . -name '*.class' | sed -e 's+^./++' -e 's/.class//' | xargs java -javaagent:../$(OSHAJAR) 

cleantests:
	rm -f test/*.class
	