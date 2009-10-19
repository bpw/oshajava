BIN=bin
SRC=src
LIBS=libs
ASMDIR=$(LIBS)/asm-3.2/lib
ASMJAR=asm-3.2.jar
ASMCOMMONSJAR=asm-commons-3.2.jar
ACMEJAR=acme-10-7-09.jar
CLASSPATH=$(ASMDIR)/$(ASMJAR):$(ASMDIR)/$(ASMCOMMONSJAR):$(ASMDIR)/asm-util-3.2.jar:$(LIBS)/$(ACMEJAR):$(BIN)
CLASSLIST=$(BIN)/classlist.txt
ANNOT_CLASSLIST=$(BIN)/annotation-classlist.txt
OSHAJAR=oshaj.jar
ANNOT_JAR=oshaj-annotation.jar
MANIFEST=$(SRC)/Manifest.txt

SETUP_SCRIPT=setup.sh

JAVAC=javac

MAIN=$(SRC)/oshaj/instrument/Instrumentor.java

build:	jar annotation-jar setupscript

jar:	classes classlist
	jar cfm $(OSHAJAR) $(MANIFEST) @$(CLASSLIST)
	jar i $(OSHAJAR)
		
classlist:	classes
	find $(BIN) -name '*.class' | sed -e 's+$(BIN)/+-C $(BIN) +' > $(CLASSLIST)

classes:	$(MAIN)
	$(JAVAC) -d $(BIN) -classpath $(CLASSPATH) -sourcepath $(SRC) $(MAIN)

## Build jar with just the oshaj.annotation package
annotation-jar: classes annotation-classlist
	jar cf $(ANNOT_JAR) @$(ANNOT_CLASSLIST)
	jar i $(ANNOT_JAR)
	
annotation-classlist:	classes
	find $(BIN)/oshaj/annotation -name '*.class' | sed -e 's+$(BIN)/+-C $(BIN) +' > $(ANNOT_CLASSLIST)

setupscript:
	echo "alias oshaj='java -javaagent:$(PWD)/oshaj.jar'" >> $(SETUP_SCRIPT)
	echo "export CLASSPATH=$$CLASSPATH:$(PWD)/oshaj-annotation.jar" >> $(SETUP_SCRIPT)

clean:
	rm -rf $(BIN)/*

distclean:
	rm -f $(OSHAJAR)
	rm -f $(SETUP_SCRIPT)

fullclean:	clean distclean cleantests

### Test stuff. ###

buildtests:
	cd test && $(JAVAC) *.java
	
runtests:	buildtests build
	cd test && find . -name '*.class' | sed -e 's+^./++' -e 's/.class//' | xargs java -javaagent:../$(OSHAJAR) 

cleantests:
	rm -f test/*.class
	