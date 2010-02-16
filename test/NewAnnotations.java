import oshajava.annotation.*;

@Group(id="MyGroup")
public class NewAnnotations {
    
    @Reader("MyGroup")
    public void someMethod() {
        System.out.println("hi there");
    }
    
}