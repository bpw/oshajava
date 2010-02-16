package oshajava.sourceinfo;

import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

import java.util.Set;

@SupportedAnnotationTypes({ "oshajava.annotation.*" })
public class OshaProcessor extends AbstractProcessor {
    
    public boolean process(
        Set<? extends TypeElement> elements,
        RoundEnvironment env
    ) {
        
        for (TypeElement te : elements) {
            for (Element e : env.getElementsAnnotatedWith(te)) {
                
                ElementKind kind = e.getKind();
                switch (e.getKind()) {
                case METHOD:
                case CONSTRUCTOR:
                    handleMethod((ExecutableElement)e);
                    break;
                    
                case CLASS:
                case INTERFACE:
                    handleClass((TypeElement)e);
                    break;
                }
                
            }
        }
        
        return true;
    }
    
    private Type typeElementToAsmType(TypeElement te) {
        switch (te.asType().getKind()) {
        
        case BOOLEAN:
            return Type.BOOLEAN_TYPE; break;
        case BYTE:
            return Type.BYTE_TYPE; break;
        case CHAR:
            return Type.CHAR_TYPE; break;
        case DOUBLE:
            return Type.DOUBLE_TYPE; break;
        case FLOAT:
            return Type.FLOAT_TYPE; break;
        case INT:
            return Type.INT_TYPE; break;
        case LONG:
            return Type.LONG_TYPE; break;
        case VOID:
            return Type.VOID_TYPE; break;
        
        case ARRAY:

        case DECLARED:
            String name = te.getQualifiedName().toString();
        
        case TYPEVAR:
        case EXECUTABLE:        
        case NULL:
        case WILDCARD:
        case OTHER:
        case PACKAGE:
        case ERROR:
            Util.fail("unsupported type kind");
        }
        
        processingEnv.getTypeUtils().
    }
    
    private void handleMethod(ExecutableElement m) {
        TypeElement cls = (TypeElement)m.getEnclosingElement();
        String name = cls.getQualifiedName() + "." + m.getSimpleName();
        
        Reader reader = m.getAnnotation(Reader.class);
        
        //System.out.println(processingEnv.getElementUtils().getBinaryName(cls));
    }
    
    private void handleClass(TypeElement e) {
        System.out.println(e + " " + e.asType());
    }
    
}