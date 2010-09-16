// from Cody
package test.compile;
import oshajava.annotation.*;

@Reader("G") // Should cascade
@Groups(communication={@Group(id="G2")}, intfc={@InterfaceGroup(id="IT2")})
@Group(id="G")
@InterfaceGroup(id="IT")
public class AnnotationTesting {
    
    @Inline
    public static void main(String[] args) {
	
    }

    @Writer({"G","IT2"})
    public static void writerTest1() {}

    @Reader({"G2","IT"})
    public static void readerTest1() {}

    @Reader({})
    @Writer({})
    public static void emptyGroupTest() {}

    public static void cascadeAnnotationTest() {}

    @NonComm
    public static void nonCommTest() {}
}

class Test {

    public static void defaultCascadedAnnotationTest() {}
}
