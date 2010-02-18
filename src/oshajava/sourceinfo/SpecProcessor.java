package oshajava.sourceinfo;

import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import java.lang.annotation.Annotation;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.util.Elements;
import javax.lang.model.SourceVersion;
import javax.tools.Diagnostic;

import com.sun.source.util.TaskListener;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.source.util.TaskEvent;

import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.io.IOException;

import oshajava.annotation.Reader;
import oshajava.annotation.Writer;
import oshajava.annotation.Inline;
import oshajava.annotation.Group;
import oshajava.annotation.InterfaceGroup;
import oshajava.annotation.Groups;
import oshajava.annotation.Member;

@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class SpecProcessor extends AbstractProcessor implements TaskListener {
    
    private Map<String, ModuleSpecBuilder> modules =
        new HashMap<String, ModuleSpecBuilder>();
    private Map<String, ModuleSpecBuilder> classToModule =
        new HashMap<String, ModuleSpecBuilder>();
    
    // TaskListener interface: callbacks for compiler events.
    public void finished(TaskEvent e) {
        if (e.getKind() == TaskEvent.Kind.GENERATE) {
            // Compilation done.
            processingEnv.getMessager().printMessage(Diagnostic.Kind.OTHER, "Writing oshajava specification.");
            Spec spec = generateSpec();
            try {
                spec.dumpModules();
            } catch (IOException t) {
                error("Problem writing spec!");
            }
        }
    }
    public void started(TaskEvent e) {
        // Ignore.
    }
    
    // Recursively visit all classes and methods.
    private void processAll(Collection<? extends Element>elements) {
        for (Element e : elements) {
            
            ElementKind kind = e.getKind();
            switch (e.getKind()) {
            case METHOD:
            case CONSTRUCTOR:
                handleMethod((ExecutableElement)e);
                break;
                
            case CLASS:
            case INTERFACE:
                handleClass((TypeElement)e);
                processAll(e.getEnclosedElements());
                break;
            
            case PACKAGE:    
                processAll(e.getEnclosedElements());
                break;
            }
            
        }
    }
    
    // Annotation processing hook.
    public boolean process(
        Set<? extends TypeElement> elements,
        RoundEnvironment env
    ) {
        // Register as a compilation event listener.
        Context ctx = ((JavacProcessingEnvironment)processingEnv).getContext();
        ctx.put(TaskListener.class, this);
        
        // Process eveything.
        processAll(env.getRootElements());
        return false;
    }
    
    /*
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
    */
    
    /**
     * Get the ModuleSpecBuilder object for the module named. If none exists,
     * one is created.
     */
    private ModuleSpecBuilder getModule(String name) {
        if (modules.containsKey(name)) {
            return modules.get(name);
        } else {
            ModuleSpecBuilder module = new ModuleSpecBuilder(name);
            modules.put(name, module);
            return module;
        }
    }
    
    /**
     * Process a class definition.
     */
    private void handleClass(TypeElement cls) {
        String name = cls.getQualifiedName().toString();
        
        // Module membership.
        Member memberAnn = cls.getAnnotation(Member.class);
        ModuleSpecBuilder module;
        if (memberAnn != null) {
            module = getModule(memberAnn.value());
        } else {
            // Default membership.
            PackageElement pkg = processingEnv.getElementUtils().getPackageOf(cls);
            module = getModule(pkg.getQualifiedName().toString());
        }
        // Facilitate module lookup for this class's methods.
        classToModule.put(name, module);
        
        // Single group declarations.
        addGroup(module, cls.getAnnotation(Group.class));
        addGroup(module, cls.getAnnotation(InterfaceGroup.class));
        // Multiple group declarations.
        Groups groupsAnn = cls.getAnnotation(Groups.class);
        if (groupsAnn != null) {
            for (Group groupAnn : groupsAnn.communication()) {
                addGroup(module, groupAnn);
            }
            for (InterfaceGroup iGroupAnn : groupsAnn.intfc()) {
                addGroup(module, iGroupAnn);
            }
        }
        
    }
    
    /**
     * Process a method declaration.
     */
    private void handleMethod(ExecutableElement m) {
        TypeElement cls = (TypeElement)m.getEnclosingElement();
        String name = cls.getQualifiedName() + "." + m.getSimpleName();
        ModuleSpecBuilder module = classToModule.get(cls.getQualifiedName().toString());
        
        assert module != null;
        
        Reader readerAnn = m.getAnnotation(Reader.class);
        Writer writerAnn = m.getAnnotation(Writer.class);
        Inline inlineAnn = m.getAnnotation(Inline.class);
        
        if ((readerAnn != null || writerAnn != null) && (inlineAnn != null)) {
            error("method " + name + " annotated as both inlined and part of a group");
        }
        
        // Group membership.
        if (readerAnn != null) {
            for (String groupId : readerAnn.value()) {
                if (!module.addReader(groupId, name)) {
                    error("no such group " + groupId + " for method " + name);
                }
            }
        }
        if (writerAnn != null) {
            for (String groupId : writerAnn.value()) {
                if (!module.addWriter(groupId, name)) {
                    error("no such group " + groupId + " for method " + name);
                }
            }
        }
        
        // Inlining (default).
        if (readerAnn == null && writerAnn == null) {
            module.inlineMethod(name);
        }
    }
    
    /**
     * Add a communicatio or interface group to a module.
     */
    private void addGroup(ModuleSpecBuilder mod, Annotation ann) {
        if (ann == null) {
            return;
        }
        
        if (ann instanceof Group) {
            Group groupAnn = (Group)ann;
            mod.addGroup(groupAnn.id(), groupAnn.delegate(), groupAnn.merge());
        } else if (ann instanceof InterfaceGroup) {
            mod.addInterfaceGroup(((InterfaceGroup)ann).id());
        } else {
            assert false;
        }
    }
    
    /**
     * Returns a Spec object reflecting all the annotations processed.
     */
    private Spec generateSpec() {
        Spec spec = new Spec();
        for (String name : modules.keySet()) {
            spec.defineModule(name, modules.get(name).generateSpec());
        }
        return spec;
    }
    
    /**
     * Print an error and stop compiling.
     */
    private void error(String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message);
    }
    
}