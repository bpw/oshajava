import oshajava.annotation.*;

@Groups(communication = {
    @Group(id="SomeGroup"),
    @Group(id="AnotherGroup")
}, intfc = {
    @InterfaceGroup(id="MyGroup"),
    @InterfaceGroup(id="YetAnotherGroup")
})
public class NewAnnotations {
    
    @Writer("MyGroup")
    public static void main(String[] argv) {
        System.out.println("hello");
    }
    
    @Reader("MyGroup")
    public void someMethod() {
        System.out.println("hi there");
    }
    
}