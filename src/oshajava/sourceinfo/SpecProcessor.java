package oshajava.sourceinfo;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;

import oshajava.annotation.Group;
import oshajava.annotation.Groups;
import oshajava.annotation.Inline;
import oshajava.annotation.InterfaceGroup;
import oshajava.annotation.Member;
import oshajava.annotation.Reader;
import oshajava.annotation.Writer;
import oshajava.support.acme.util.Util;
import oshajava.util.ColdStorage;

import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.Context;

@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class SpecProcessor extends AbstractProcessor implements TaskListener {
    
    private Map<String, ModuleSpecBuilder> modules =
        new HashMap<String, ModuleSpecBuilder>();
    private Map<String, ModuleSpecBuilder> classToModule =
        new HashMap<String, ModuleSpecBuilder>();

    private final Set<ModuleSpecBuilder> changed = new HashSet<ModuleSpecBuilder>();
    // TaskListener interface: callbacks for compiler events.
    public void finished(TaskEvent e) {
        if (e.getKind() == TaskEvent.Kind.GENERATE) {
            // Compilation done.
//            processingEnv.getMessager().printMessage(Diagnostic.Kind.OTHER, "Writing oshajava specification.");
            for (ModuleSpecBuilder mod : changed) {
            	try {
           			note("Writing " + mod.getName());
					mod.write();
				} catch (IOException e1) {
					processingEnv.getMessager().printMessage(Diagnostic.Kind.OTHER, "Failed to write " + mod.getName() + ModuleSpecBuilder.EXT + ".");
					e1.printStackTrace();
				}
            }
        	changed.clear();
        }
    }
    public void started(TaskEvent e) {
        // Ignore.
    }
    
    
    // Recursively visit all classes and methods.
    private void processAll(Collection<? extends Element>elements) {
        for (Element e : elements) {
            
//            ElementKind kind = e.getKind();
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
    private ModuleSpecBuilder getModule(String qualifiedName) {
    	if (modules.containsKey(qualifiedName)) {
    		return modules.get(qualifiedName);
    	} else {
    		ModuleSpecBuilder module;
    		final int lastDot = qualifiedName.lastIndexOf('.');
    		// package name of module
    		final String pkg = lastDot == -1 ? "" : qualifiedName.substring(0, lastDot);
    		// relative name of module
    		final String simpleName = lastDot == -1 ? qualifiedName : qualifiedName.substring(lastDot + 1);
    		try {
    			// get the file it should be dumped in.
//    			Util.logf("pkg: %s relname: %s", pkg, simpleName);
    			final URI uri = processingEnv.getFiler().getResource(StandardLocation.locationFor("CLASS_OUTPUT"), 
    					pkg, simpleName + ModuleSpecBuilder.EXT).toUri();
       			module = (ModuleSpecBuilder)ColdStorage.load(uri);
       			note("Read " + qualifiedName);
       			Util.assertTrue(uri.equals(module.getURI()));
    		} catch (IOException e) {
    			// File did not exist. Create new module and its file.
    			try {
					module = new ModuleSpecBuilder(qualifiedName,
							processingEnv.getFiler().createResource(StandardLocation.locationFor("CLASS_OUTPUT"), 
									pkg, simpleName + ModuleSpecBuilder.EXT).toUri());
					changed.add(module);
				} catch (IOException e1) {
					throw new RuntimeException(e1);
				}
    		} catch (ClassNotFoundException e) {
				throw new RuntimeException(e);
			}
        	modules.put(qualifiedName, module);
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
            String pkgName = pkg.getQualifiedName().toString();
            module = getModule(pkgName + (pkgName.isEmpty() ? "" : ".") + ModuleSpec.DEFAULT_NAME);
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
                } else {
                    changed.add(module);
                }
            }
        }
        if (writerAnn != null) {
            for (String groupId : writerAnn.value()) {
                if (!module.addWriter(groupId, name)) {
                    error("no such group " + groupId + " for method " + name);
                } else {
                    changed.add(module);
                }
            }
        }
        
        // Inlining (default).
        if (readerAnn == null && writerAnn == null) {
            module.inlineMethod(name);
            changed.add(module);
        }
    }
    
    /**
     * Add a communication or interface group to a module.
     */
    private void addGroup(ModuleSpecBuilder mod, Annotation ann) {
        if (ann == null) {
            return;
        }
        
        if (ann instanceof Group) {
            Group groupAnn = (Group)ann;
            mod.addGroup(groupAnn.id(), groupAnn.delegate(), groupAnn.merge());
            changed.add(mod);
        } else if (ann instanceof InterfaceGroup) {
            mod.addInterfaceGroup(((InterfaceGroup)ann).id());
            changed.add(mod);
        } else {
            assert false;
        }
    }
    
    /**
     * Print an error and stop compiling.
     */
    private void error(String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message);
    }
    private void note(String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, message);
    }
    
}