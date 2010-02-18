import oshajava.annotation.*;

@InterfaceGroup(id="MyGroup")
public class NewAnnotations {
    
    @Reader("MyGroup")
    public void someMethod() {
        System.out.println("hi there");
    }
    
}