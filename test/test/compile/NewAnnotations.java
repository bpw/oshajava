package test.compile;
import oshajava.annotation.*;
import java.util.Set;

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
    public Integer someMethod(Set<String> a) {
        System.out.println("hi there");
        return null;
    }
    
}