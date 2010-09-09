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
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.AbstractElementVisitor6;
import javax.lang.model.util.ElementScanner6;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;

import oshajava.annotation.Group;
import oshajava.annotation.Groups;
import oshajava.annotation.Inline;
import oshajava.annotation.InterfaceGroup;
import oshajava.annotation.Module;
import oshajava.annotation.NonComm;
import oshajava.annotation.Reader;
import oshajava.annotation.Writer;
import oshajava.instrument.InstrumentationAgent;
import oshajava.support.acme.util.Util;
import oshajava.util.ColdStorage;

@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_6)
@SupportedOptions({SpecProcessor.DEFAULT_ANN_OPTION, SpecProcessor.DEBUG_OPTION})
public class SpecProcessor extends AbstractProcessor {
	
	public static final String DEFAULT_ANN_OPTION = "oshajava.annotation.default",
		DEBUG_OPTION = "oshajava.verbose";
	
	private static final String INLINE_ANN = "inline", NONCOMM_ANN = "noncomm";

	private final Map<String, ModuleSpecBuilder> modules = new HashMap<String, ModuleSpecBuilder>();
//	private final Map<String, String> packageToModule = new HashMap<String, String>();
	private final Map<Element, ModuleSpecBuilder> processedModules = new HashMap<Element, ModuleSpecBuilder>();
	private final ModuleScanner moduleScanner = new ModuleScanner();
	private final GroupScanner groupScanner = new GroupScanner();
	private final GroupMembershipScanner groupMembershipScanner = new GroupMembershipScanner();
	
//	private final Set<ModuleSpecBuilder> changed = new HashSet<ModuleSpecBuilder>();
	
	private boolean verbose;
	
	@Override
	public synchronized void init(ProcessingEnvironment env) {
		super.init(env);
		// Set the default annotation to @Inline or @NonComm.
		// e.g. -Aoshajava.annotation.default=noncomm
		final String defaultAnn = env.getOptions().get(DEFAULT_ANN_OPTION);
		if (defaultAnn == null || defaultAnn.toLowerCase().equals(INLINE_ANN)) {
			ModuleSpecBuilder.setDefaultInline(true);
		} else if (defaultAnn.toLowerCase().equals(NONCOMM_ANN)) {
			ModuleSpecBuilder.setDefaultInline(false);			
		} else {
			throw new IllegalArgumentException(DEFAULT_ANN_OPTION + "=" + defaultAnn);
		}
		verbose = env.getOptions().containsKey(DEBUG_OPTION);
	}

	/**
	 *  Annotation processing hook.
	 *  
	 *  NOTE: We assume that the list returned by env.getRootElements() ALWAYS lists outer class before inner class. 
	 *  This is what we have observed in practice.
	 */
	@Override
	public boolean process(Set<? extends TypeElement> annotationTypes, RoundEnvironment env) {
		// Packages, classes, and interfaces to process in this round.
		final Set<? extends Element> elements = env.getRootElements();
		if (elements.isEmpty()) {
			// If empty, then processing is done. Dump the modules to disk.
			dumpChanges();
		} else {
			// First, establish modules.
			for (final Element e : elements) {
				moduleScanner.scan(e);
			}
			// Second, establish groups.
			for (final Element e : elements) {
				groupScanner.scan(e);
			}			
			// Third, establish readers and writers in groups, noncomm, and inline.
			for (final Element e : elements) {
				groupMembershipScanner.scan(e);
			}
		}
		// Let other processors see the annotations.
		return false;
	}
	
	/**
	 * Convenience...
	 * @author bpw
	 *
	 * @param <R>
	 * @param <P>
	 */
	class MyElementScanner<R,P> extends ElementScanner6<R, P> {
		@Override
		public R visitExecutable(ExecutableElement e, P p) { return null; }
		@Override
		public final R visitVariable(VariableElement e, P p) { return null; }
		@Override
		public final R visitTypeParameter(TypeParameterElement e, P p) { return null; }
	}

	/**
	 * Scans Element tree for module membership declarations.
	 * @author bpw
	 *
	 */
	class ModuleScanner extends MyElementScanner<ModuleSpecBuilder, ModuleSpecBuilder> {
		@Override
		public ModuleSpecBuilder scan(Element e, ModuleSpecBuilder p) {
			if (! processedModules.containsKey(e)) {
				final Module memberDecl = e.getAnnotation(Module.class);
				final ModuleSpecBuilder module;
				if (memberDecl != null) {
					module = getModuleByName(memberDecl.value());
					processedModules.put(e, module);
					super.scan(e, module);
					return module;
				} else {
					module = super.scan(e, p);
					processedModules.put(e, module);
				}
				return module;
			}
			return processedModules.get(e);
		}
		
		@Override
		public ModuleSpecBuilder visitPackage(PackageElement pkg, ModuleSpecBuilder p) {
			return getModuleByName(pkg.isUnnamed() ? ModuleSpec.DEFAULT_NAME : pkg.getQualifiedName() + "." + ModuleSpec.DEFAULT_NAME);
		}

		@Override
		public ModuleSpecBuilder visitType(TypeElement e, ModuleSpecBuilder p) {
			if (p == null) {
				p = scan(processingEnv.getElementUtils().getPackageOf(e));
			}
			super.visitType(e, p);
			return p;
		}

		@Override
		public ModuleSpecBuilder visitExecutable(ExecutableElement e, ModuleSpecBuilder p) {
			return p;
		}

	}
	
	class GroupScanner extends MyElementScanner<Object, Object> {

		private final Set<Element> processedGroups = new HashSet<Element>();

		@Override
		public Object scan(Element e, Object p) {
			if (! processedGroups.contains(e)) {
				processedGroups.add(e);
				final ModuleSpecBuilder module = processedModules.get(e);
				// Handle any single group declarations.
				addGroup(module, e.getAnnotation(Group.class));
				addGroup(module, e.getAnnotation(InterfaceGroup.class));
				// Handle multiple group declarations.
				Groups groupsAnn = e.getAnnotation(Groups.class);
				if (groupsAnn != null) {
					for (Group groupAnn : groupsAnn.communication()) {
						addGroup(module, groupAnn);
					}
					for (InterfaceGroup iGroupAnn : groupsAnn.intfc()) {
						addGroup(module, iGroupAnn);
					}
				}
				super.scan(e, p);
			}
			return null;
		}
		
	}
	
	class GroupMembershipScanner extends MyElementScanner<Object, Object> {

		private final Set<Element> processedGroupMemberships = new HashSet<Element>();

		@Override
		public Object scan(Element e, Object p) {
			if (!processedGroupMemberships.contains(e)) {
				processedGroupMemberships.add(e);
				super.scan(e, p);
			}
			return null;
		}

		@Override
		public Object visitExecutable(ExecutableElement m, Object p) {
			// Get the right module.
			final ModuleSpecBuilder module = processedModules.get(m);

			
			TypeElement cls = (TypeElement)m.getEnclosingElement();
			String sig = DescriptorWrangler.methodDescriptor(cls, m);
			

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
					module.ctrGroupMembership++;
				}
				nonEmptyGroups = readerAnn.value() != null && readerAnn.value().length > 0;
			}
			if (writerAnn != null) {
				for (String groupId : writerAnn.value()) {
					module.addWriter(groupId, sig);
					module.ctrGroupMembership++;
				}
				nonEmptyGroups = nonEmptyGroups || (writerAnn.value() != null && writerAnn.value().length > 0);
			}

			// Explicit Non-comm
			if (nonCommAnn != null || ((writerAnn != null || readerAnn != null) && !nonEmptyGroups)) {
				module.addNonComm(sig);
			}

			// Default (unannotated)
			if (readerAnn == null && writerAnn == null && nonCommAnn == null && inlineAnn == null) {
				module.addUnannotatedMethod(sig);
			}

			// Explicit Inline
			if (inlineAnn != null) {
				module.inlineMethod(sig);
			}

			
			return null;
		}

		@Override
		public Object visitPackage(PackageElement e, Object p) {
			// TODO:  Allow cascading defaults?
			return super.visitPackage(e, p);
		}

		@Override
		public Object visitType(TypeElement e, Object p) {
			// TODO:  Allow cascading defaults?
			return super.visitType(e, p);
		}
		
	}
	
	private void dumpChanges() {
		for (ModuleSpecBuilder mod : modules.values()) {
			try {
				note("Writing " + mod.getName());
				note("  " + mod.summary());
				mod.write(this);
				if (verbose) {
					note(mod.generateSpec().toString());
				}
			} catch (IOException e1) {
				processingEnv.getMessager().printMessage(Diagnostic.Kind.OTHER, "Failed to write " + mod.getName() + ModuleSpecBuilder.EXT + " or " + mod.getName() + ModuleSpec.EXT + ".");
				e1.printStackTrace();
			}
		}
//		changed.clear();
	}

	/**
	 * Get the ModuleSpecBuilder object for the module named. If none exists,
	 * one is created.
	 */
	private ModuleSpecBuilder getModuleByName(String qualifiedName) {
		if (modules.containsKey(qualifiedName)) {
			return modules.get(qualifiedName);
		} else {
			ModuleSpecBuilder module;
			final int lastDot = qualifiedName.lastIndexOf('.');
			// package name of module
			final String pkg = lastDot == -1 ? "" : qualifiedName.substring(0, lastDot);
			// simple name of module
			final String simpleName = lastDot == -1 ? qualifiedName : qualifiedName.substring(lastDot + 1);
			final String location = "SOURCE_OUTPUT"; //  XXX Cody: I changed CLASS_OUTPUT to SOURCE_OUTPUT so that the files go to the right places.
			try {
				// get the file it should be dumped in.
				//    			Util.logf("pkg: %s relname: %s", pkg, simpleName);
				URI uri = processingEnv.getFiler().getResource(StandardLocation.locationFor(location),
						pkg, simpleName + ModuleSpecBuilder.EXT).toUri();
				uri = new File(uri.getPath()).getAbsoluteFile().toURI(); // ensure the URI is absolute
				module = (ModuleSpecBuilder)ColdStorage.load(uri);
				note("Read " + qualifiedName);
				Util.assertTrue(uri.equals(module.getURI()));
			} catch (IOException e) {
				// File did not exist. Create new module and its file.
				try {
					URI uri = processingEnv.getFiler().createResource(StandardLocation.locationFor(location),
							pkg, simpleName + ModuleSpecBuilder.EXT).toUri();
					uri = new File(uri.getPath()).getAbsoluteFile().toURI();
					module = new ModuleSpecBuilder(qualifiedName, uri);
//					changed.add(module);
				} catch (IOException e1) {
					Util.log("SpecProcessor.getModule(\""+ qualifiedName + "\") failing...");
					throw new RuntimeException(e1);
				}
			} catch (ClassNotFoundException e) {
				Util.log("SpecProcessor.getModule(\""+ qualifiedName + "\") failing...");
				throw new RuntimeException(e);
			}
			modules.put(qualifiedName, module);
			return module;
		}
	}

	/**
	 * Add a communication or interface group to a module.
	 */
	private void addGroup(ModuleSpecBuilder mod, Annotation ann) {
		if (ann == null) {
			return;
		}

		mod.ctrGroupDeclaration++;

		if (ann instanceof Group) {
			Group groupAnn = (Group)ann;
			mod.addGroup(groupAnn.id());
//			changed.add(mod);
		} else if (ann instanceof InterfaceGroup) {
			mod.addInterfaceGroup(((InterfaceGroup)ann).id());
//			changed.add(mod);
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
	public void note(String message) {
		processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, message);
	}

}