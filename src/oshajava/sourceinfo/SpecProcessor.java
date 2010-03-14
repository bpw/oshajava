package oshajava.sourceinfo;

import java.io.IOException;
import java.io.File;
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
import javax.lang.model.element.VariableElement;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;

import oshajava.annotation.Group;
import oshajava.annotation.Groups;
import oshajava.annotation.Inline;
import oshajava.annotation.InterfaceGroup;
import oshajava.annotation.Member;
import oshajava.annotation.NonComm;
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
           			note("  " + mod.summary());
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
            case ENUM:
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
    
    /**
     * Construct the JVM type descriptor for a TypeMirror.
     */
    private String typeDescriptor(TypeMirror tm) {
        // Reference:
        // http://java.sun.com/docs/books/jvms/second_edition/html/ClassFile.doc.html#1169
        // http://www.ibm.com/developerworks/java/library/j-cwt02076.html
        switch (tm.getKind()) {
        case BOOLEAN:
            return "Z";
        case BYTE:
            return "B";
        case CHAR:
            return "C";
        case DOUBLE:
            return "D";
        case FLOAT:
            return "F";
        case INT:
            return "I";
        case LONG:
            return "J";
        case VOID:
            return "V";
        
        case ARRAY:
            return "[" + typeDescriptor(((ArrayType)tm).getComponentType());

        case DECLARED:
            DeclaredType decl = (DeclaredType)tm;
            
            String name = decl.toString();
            int lt = name.indexOf('<');
            if (lt != -1) {
                // This is a parameterized type. Remove the <...> part.
                name = name.substring(0, lt);
            }
            
            name = name.replace('.', '/');
            
            // Disable <...> appending, because ASM doesn't seem to do it?
            /*
            if (!decl.getTypeArguments().isEmpty()) {
                // Add back type parameters.
                name += "<";
                for (TypeMirror arg : decl.getTypeArguments()) {
                    name += typeDescriptor(arg);
                }
                name += ">";
            }
            */
            
            // Check if it's an iinner class.
            TypeMirror encloser = null;
            if (decl.getEnclosingType().getKind() != TypeKind.NONE) {
                encloser = decl.getEnclosingType();
            } else if (decl.asElement().getEnclosingElement()
                       instanceof TypeElement) {
                encloser = decl.asElement().getEnclosingElement().asType();
            }
            
            if (encloser != null) {
                // This is an inner class.
                int lastSlash = name.lastIndexOf('/');
                String baseName = name.substring(lastSlash + 1);
                String enclosingName = typeDescriptor(encloser);
                // Remove L and ; on either end of encloser descriptor.
                enclosingName = enclosingName.substring(1,
                    enclosingName.length() - 1);
                name = enclosingName + "$" + baseName;
            }
            
            return "L" + name + ";";
        
        case TYPEVAR:
            return "T" + tm.toString() + ";";
            
        case WILDCARD:
            return "?";
            
        case EXECUTABLE:        
        case NULL:
        case OTHER:
        case PACKAGE:
        case ERROR:
        default:
            return null;
        }
        
    }
    
    /**
     * Construct the JVM method descriptor for an ExecutableElement.
     */
    private String methodDescriptor(TypeElement cls, ExecutableElement m) {
        // Container name.
        String out = typeDescriptor(cls.asType());
        // Remove L and ; from container class.
        out = out.substring(1, out.length() - 1);
        
        // Method name.
        out += "." + m.getSimpleName();
        
        // Parameter and return types.
        out += "(";
        // Special case for enumeration constructors.
        if (cls.getKind() == ElementKind.ENUM &&
                    m.getSimpleName().toString().equals("<init>")) {
            // For some reason, the annotation processing system seems
            // to miss some enum constructor parameters!
            out += "Ljava/lang/String;I";
        }
        // Special case for non-static inner class constructors.
        if (cls.getNestingKind() == NestingKind.MEMBER &&
                    !cls.getModifiers().contains(Modifier.STATIC) &&
                    m.getSimpleName().toString().equals("<init>")) {
            // Annotation processing is also not aware that inner class
            // constructors get their outer class passed as a parameter.
            out += typeDescriptor(cls.getEnclosingElement().asType());
        }
        for (VariableElement ve : m.getParameters()) {
            out += typeDescriptor(ve.asType());
        }
        out += ")" + typeDescriptor(m.getReturnType());
        return out;
    }
    
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
    			URI uri = processingEnv.getFiler().getResource(StandardLocation.locationFor("CLASS_OUTPUT"), 
    					pkg, simpleName + ModuleSpecBuilder.EXT).toUri();
    			uri = new File(uri.getPath()).getAbsoluteFile().toURI(); // ensure the URI is absolute
       			module = (ModuleSpecBuilder)ColdStorage.load(uri);
       			note("Read " + qualifiedName);
       			Util.assertTrue(uri.equals(module.getURI()));
    		} catch (IOException e) {
    			// File did not exist. Create new module and its file.
    			try {
    			    URI uri = processingEnv.getFiler().createResource(StandardLocation.locationFor("CLASS_OUTPUT"), 
							pkg, simpleName + ModuleSpecBuilder.EXT).toUri();
					uri = new File(uri.getPath()).getAbsoluteFile().toURI();
					module = new ModuleSpecBuilder(qualifiedName, uri);
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
        PackageElement pkg = processingEnv.getElementUtils().getPackageOf(cls);
        String pkgName = pkg.getQualifiedName().toString();
        if (!pkgName.isEmpty()) {
        	pkgName += ".";
        }
        if (memberAnn != null) {
        	String modName = memberAnn.value();
        	if (modName.contains(".")) {
        		module = getModule(modName);
        	} else {
        		module = getModule(pkgName + modName);
        	}
        } else {
            // Default membership.
            module = getModule(pkgName + ModuleSpec.DEFAULT_NAME);
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
//        String name = cls.getQualifiedName() + "." + m.getSimpleName();
        ModuleSpecBuilder module = classToModule.get(cls.getQualifiedName().toString());
        String sig = methodDescriptor(cls, m);
        
        assert module != null;
        
        Reader readerAnn = m.getAnnotation(Reader.class);
        Writer writerAnn = m.getAnnotation(Writer.class);
        Inline inlineAnn = m.getAnnotation(Inline.class);
        NonComm nonCommAnn = m.getAnnotation(NonComm.class);
        
        if (((readerAnn != null || writerAnn != null) && (inlineAnn != null || nonCommAnn != null))
        		|| (inlineAnn != null && nonCommAnn != null)) {
            error("method " + sig + ": conflicting annotations. @Inline, @NonComm, and @Writer/@Reader are mutually exclusive.");
        }
        
        boolean nonEmptyGroups = false;
        // Group membership.
        if (readerAnn != null) {
            for (String groupId : readerAnn.value()) {
                module.addReader(groupId, sig);
            }
            nonEmptyGroups = readerAnn.value() != null && readerAnn.value().length > 0;
        }
        if (writerAnn != null) {
            for (String groupId : writerAnn.value()) {
                module.addWriter(groupId, sig);
            }
            nonEmptyGroups = nonEmptyGroups || (writerAnn.value() != null && writerAnn.value().length > 0);
        }
        
        // Non-comm
        if (((writerAnn != null || readerAnn != null) && !nonEmptyGroups) || nonCommAnn != null) {
        	module.addNonComm(sig);
        }
        
        // Inlining (default).
        if (readerAnn == null && writerAnn == null && nonCommAnn == null) {
            module.inlineMethod(sig);
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