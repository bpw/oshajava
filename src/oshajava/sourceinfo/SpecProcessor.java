package oshajava.sourceinfo;

import java.io.IOException;
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
import javax.lang.model.element.UnknownElementException;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileManager.Location;
import javax.tools.StandardLocation;

import oshajava.annotation.Groups;
import oshajava.annotation.Inline;
import oshajava.annotation.InterfaceGroup;
import oshajava.annotation.NonComm;
import oshajava.annotation.Reader;
import oshajava.annotation.Writer;
import oshajava.sourceinfo.Module.DuplicateGroupException;
import oshajava.sourceinfo.Module.DuplicateMethodException;
import oshajava.sourceinfo.SpecFileManager.Creator;

@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_6)
@SupportedOptions({SpecProcessor.DEFAULT_ANN_OPTION, SpecProcessor.DEBUG_OPTION})
public class SpecProcessor extends AbstractProcessor {
	
	public static final String DEFAULT_ANN_OPTION = "oshajava.annotation.default",
		DEBUG_OPTION = "oshajava.verbose";
	
	private static final String INLINE_ANN = "inline", NONCOMM_ANN = "noncomm";
	
	private static final Location OUTPUT = StandardLocation.SOURCE_OUTPUT;

	private SpecFileManager<Module> modules;
	private SpecFileManager<ModuleMap> maps;
	private SpecFileManager<CompiledModuleSpec> moduleSpecs;
	private final Map<Element, Module> processedModules = new HashMap<Element, Module>();
	private final ModuleTraversal moduleScanner = new ModuleTraversal();
	private final GroupTraversal groupScanner = new GroupTraversal();
	private final CommTraversal groupMembershipScanner = new CommTraversal();
	
	private boolean verbose;
	
	@Override
	public synchronized void init(ProcessingEnvironment env) {
		super.init(env);
		modules = new SpecFileManager<Module>(Module.EXT, new Creator<Module>() {
			public Module create(final String qualifiedName) {
				return new Module(qualifiedName);
			}
		}, env, OUTPUT, false); // FIXME: Why source output and not class output?  
		// Cody switched to source output because something wasn't working.
		maps = new SpecFileManager<ModuleMap>(ModuleMap.EXT, new Creator<ModuleMap>() {
			public ModuleMap create(final String className) {
				return new ModuleMap(className);
			}
		}, env, OUTPUT, true);
		moduleSpecs = new SpecFileManager<CompiledModuleSpec>(CompiledModuleSpec.EXT, env, OUTPUT);
		
		// Set the default annotation to @Inline or @NonComm.
		// e.g. -Aoshajava.annotation.default=noncomm
		final String defaultAnn = env.getOptions().get(DEFAULT_ANN_OPTION);
		if (defaultAnn == null || defaultAnn.toLowerCase().equals(INLINE_ANN)) {
			Module.setDefaultInline(true);
		} else if (defaultAnn.toLowerCase().equals(NONCOMM_ANN)) {
			Module.setDefaultInline(false);			
		} else {
			throw new IllegalArgumentException(DEFAULT_ANN_OPTION + "=" + defaultAnn);
		}
		verbose = env.getOptions().containsKey(DEBUG_OPTION);
	}

	/**
	 * Print an error and stop compiling.
	 */
	public void error(String message) {
		processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message);
	}
	public void note(String message) {
		processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, message);
	}

	/**
	 *  Annotation processing hook.
	 *  
	 *  NOTE: We assume that the list returned by env.getRootElements() ALWAYS lists outer class before inner class. 
	 *  This is what we have observed in practice.
	 */
	@Override
	public boolean process(Set<? extends TypeElement> annotationTypes, RoundEnvironment round) {
		// Packages, classes, and interfaces to process in this round.
		final Set<? extends Element> elements = round.getRootElements();
		if (elements.isEmpty()) {
			// If empty, then processing is done. Flush the spec files to disk.
			try {
				modules.flushAll();
				for (Module m : modules) {
					CompiledModuleSpec ms = m.generateSpec();
					moduleSpecs.save(ms);
					if (verbose) {
						System.out.println(ms);
					}
				}
				maps.flushAll();
				note("Compiled " + modules.size() + " OSHA modules from " + maps.size() + " classes.");
			} catch (IOException e) {
				error("Failed to write spec to disk due to IOException.");
				e.printStackTrace();
			}
		} else {
			// First, establish modules.
			for (final Element e : elements) {
				moduleScanner.traverse(e);
			}
			// Map methods and constructors to their modules.
			for (final ExecutableElement e : ElementFilter.methodsIn(processedModules.keySet())) {
				TypeElement cls = (TypeElement)e.getEnclosingElement();
				maps.getOrCreate(cls.getQualifiedName().toString()).put(DescriptorWrangler.methodDescriptor(cls, e), processedModules.get(e).getName());
			}
			for (final ExecutableElement e : ElementFilter.constructorsIn(processedModules.keySet())) {
				TypeElement cls = (TypeElement)e.getEnclosingElement();
				maps.getOrCreate(cls.getQualifiedName().toString()).put(DescriptorWrangler.methodDescriptor(cls, e), processedModules.get(e).getName());	
			}
			// Second, establish groups.
			for (final Element e : elements) {
				groupScanner.traverse(e);
			}			
			// Third, establish readers and writers in groups, noncomm, and inline.
			for (final Element e : elements) {
				groupMembershipScanner.traverse(e);
			}
		}
		// Let other processors see the annotations.
		return false;
	}
	
	abstract class Traversal {
		
		protected abstract boolean done(Element e);
		/**
		 * Invariant: when handle(e) is called, either parent == null or all of e's ancestors have been handled already.
		 * @param e
		 */
		protected abstract void handle(Element e);
		
		public void traverse(Element e) {
			if (!done(e)) {
				// If this element is not already done:
				final Element parent = e.getEnclosingElement();
				if (parent != null && !done(parent)) {
					// Parent is unlabeled: handle parent.
					handle(parent);
				}
				handle(e);
				// Traverse children.
				for (Element child : e.getEnclosedElements()) {
					traverse(child);
				}
			}
		}
	}
	
	abstract class LabelingTraversal<R> extends Traversal implements ElementVisitor<R,Object> {
		
		protected boolean done(Element e) {
			return getLabel(e) != null;
		}
		protected abstract R getLabelFromAnnotation(Element e);
		protected abstract void setLabel(Element e, R r);
		protected abstract R getLabel(Element e);
		
		/**
		 * Merge an elements label with that inherited from its parent.
		 * Default behavior is to overwrite the inherited label with the local label if the
		 * local label exists.
		 * 
		 * @param label
		 * @param parentLabel
		 * @return
		 */
		protected R mergeLabels(R label, R parentLabel) {
			return label != null ? label : parentLabel;
		}
		
		/**
		 * Handle an element e.
		 */
		public void handle(Element e) {
			// If this element is not already done:
			final R label = getLabelFromAnnotation(e);
			final Element parent = e.getEnclosingElement();
			// Merge the label of this element and its parent.
			final R mergedLabel = mergeLabels(label, parent == null ? null : getLabel(parent));
			// If this is still empty, generate a default label.
			setLabel(e, mergedLabel != null ? mergedLabel : visit(e));
		}

		public R visitPackage(PackageElement e, Object _) { throw new IllegalStateException("package " + e.getQualifiedName()); }
		public R visitType(TypeElement e, Object _) { throw new IllegalStateException("type " + e.getQualifiedName() + " parent " +
				e.getEnclosingElement() + " parent done " + done(e.getEnclosingElement()) + " parent label " + getLabel(e.getEnclosingElement())); }
		public R visitVariable(VariableElement e, Object _) { throw new IllegalStateException("var"); }
		public R visitExecutable(ExecutableElement e, Object _) { throw new IllegalStateException("method " + e.getSimpleName()); }
		public R visitTypeParameter(TypeParameterElement e, Object _) { throw new IllegalStateException("type param"); }

		public R visit(Element e, Object p) {
			return e.accept(this, p);
		}

		public R visit(Element e) {
			return visit(e, null);
		}

		public R visitUnknown(Element e, Object p) {
			throw new UnknownElementException(e, p);
		}
		
}

	
	class ModuleTraversal extends LabelingTraversal<Module> {

		@Override
		protected Module getLabelFromAnnotation(Element e) {
			final oshajava.annotation.Module memberDecl = e.getAnnotation(oshajava.annotation.Module.class);
			if (memberDecl != null) {
				return modules.getOrCreate(memberDecl.value());
			} else {
				return null;
			}
		}

		@Override
		protected void setLabel(Element e, Module r) {
			processedModules.put(e, r);
		}

		@Override
		protected Module getLabel(Element e) {
			return e == null ? null : processedModules.get(e);
		}

		@Override
		public Module visitPackage(PackageElement e, Object _) {
			return modules.getOrCreate(e.isUnnamed() ? CompiledModuleSpec.DEFAULT_NAME : e.getQualifiedName() + "." + CompiledModuleSpec.DEFAULT_NAME);
		}

	}
	
	class GroupTraversal extends Traversal {
		private final HashSet<Element> done = new HashSet<Element>();
		private final HashMap<String,Element> groups = new HashMap<String,Element>();

		@Override
		protected boolean done(Element e) {
			return done.contains(e);
		}

		@Override
		protected void handle(Element e) {
			final Module module = processedModules.get(e);
			{
				final oshajava.annotation.Group groupAnn = e.getAnnotation(oshajava.annotation.Group.class);
				if (groupAnn != null) {
					try {
						module.addCommGroup(groupAnn.id());
						groups.put(groupAnn.id(), e);
					} catch (DuplicateGroupException e1) {
						processingEnv.getMessager().printMessage(Kind.ERROR, groupAnn + " conflicts with subsequent group declaration.", groups.get(groupAnn.id()));
						processingEnv.getMessager().printMessage(Kind.ERROR, groupAnn + " conflicts with previous group declaration.", e);
					}
				}
			}
			{
				final InterfaceGroup interfaceAnn = e.getAnnotation(InterfaceGroup.class);
				if (interfaceAnn != null) {
					try {
						module.addInterfaceGroup(interfaceAnn.id());
						groups.put(interfaceAnn.id(), e);
					} catch (DuplicateGroupException e1) {
						processingEnv.getMessager().printMessage(Kind.ERROR, interfaceAnn + " conflicts with subsequent group declaration.", groups.get(interfaceAnn.id()));
						processingEnv.getMessager().printMessage(Kind.ERROR, interfaceAnn + " conflicts with previous group declaration.", e);
					}
				}
			}
			Groups groupsAnn = e.getAnnotation(Groups.class);
			if (groupsAnn != null) {
				for (oshajava.annotation.Group g : groupsAnn.communication()) {
					try {
						module.addCommGroup(g.id());
						groups.put(g.id(), e);
					} catch (DuplicateGroupException e1) {
						processingEnv.getMessager().printMessage(Kind.ERROR, g + " conflicts with subsequent group declaration.", groups.get(g.id()));
						processingEnv.getMessager().printMessage(Kind.ERROR, g + " conflicts with previous group declaration.", e);
					}
				}
				for (InterfaceGroup i : groupsAnn.intfc()) {
					try {
						module.addInterfaceGroup(i.id());
						groups.put(i.id(), e);
					} catch (DuplicateGroupException e1) {
						processingEnv.getMessager().printMessage(Kind.ERROR, i + " conflicts with subsequent group declaration.", groups.get(i.id()));
						processingEnv.getMessager().printMessage(Kind.ERROR, i + " conflicts with previous group declaration.", e);
					}
				}
			}
			done.add(e);
		}

	}

	class CommTraversal extends LabelingTraversal<MethodSpec> {
		
		private final Map<Element, MethodSpec> defaultMethodSpecs = new HashMap<Element, MethodSpec>();

		@Override
		protected MethodSpec getLabelFromAnnotation(Element e) {
			Reader reader = e.getAnnotation(Reader.class);
			Writer writer = e.getAnnotation(Writer.class);
			Inline inline = e.getAnnotation(Inline.class);
			NonComm noncomm = e.getAnnotation(NonComm.class);
			final Module module = processedModules.get(e);
			MethodSpec ms;
			try {
			if (inline != null) {
				if (noncomm == null && reader == null && writer == null) {
					ms = MethodSpec.INLINE;
				} else {
					ms = MethodSpec.ERROR;
				}
			} else if (noncomm != null) {
				if (reader == null && writer == null) {
					ms = MethodSpec.NONCOMM;
				} else {
					ms = MethodSpec.ERROR;
				}
			} else if (reader != null || writer != null) {
				Set<Group> readGroups = null, writeGroups = null;
				if (reader != null) {
					readGroups = new HashSet<Group>();
					for (String readGroup : reader.value()) {
						readGroups.add(module.getGroup(readGroup));
					}
				}
				if (writer != null) {
					 writeGroups = new HashSet<Group>();
					 for (String writeGroup : writer.value()) {
						writeGroups.add(module.getGroup(writeGroup));
					}
				}
				if ((readGroups == null || readGroups.isEmpty()) && (writeGroups == null || writeGroups.isEmpty())) {
					ms = MethodSpec.NONCOMM;
				} else {
					ms = new MethodSpec(MethodSpec.Kind.COMM, readGroups, writeGroups);
				}
			} else {
				ms = MethodSpec.DEFAULT;
			}
			} catch (Module.GroupNotFoundException e1) {
				ms = MethodSpec.ERROR;
			}
			if (ms == MethodSpec.ERROR) {
				error(e.getSimpleName() + ": conflicting annotations. @Inline, @NonComm, and @Writer/@Reader are mutually exclusive.");
				return null;
			}
			return ms;
		}

		@Override
		protected MethodSpec mergeLabels(MethodSpec label, MethodSpec parentLabel) {
			if (label == null) return parentLabel;
			if (parentLabel == null) return label;
			switch (label.kind()) {
			case INLINE:
			case NONCOMM:
				return label;
			case COMM:
				if (parentLabel.kind() == MethodSpec.Kind.COMM) {
					return new MethodSpec(MethodSpec.Kind.COMM, label.readGroups() == null ? parentLabel.readGroups() : label.readGroups(), 
							label.writeGroups() == null ? parentLabel.writeGroups() : label.writeGroups());
				} else {
					return label;
				}
//			case DEFAULT: // TODO reinstate with @Default?
//				return defaultAnnotation();
			default:
				return defaultAnnotation(); // FIXME correct?
			}
		}

		@Override
		protected void setLabel(Element e, MethodSpec r) {
			defaultMethodSpecs.put(e, r);
			if (e instanceof ExecutableElement) {
				final ExecutableElement m = (ExecutableElement)e;
				// Get the right module.
				final Module module = processedModules.get(m);
				TypeElement cls = (TypeElement)e.getEnclosingElement();
				String sig = DescriptorWrangler.methodDescriptor(cls, m);
				try {
					module.addMethod(sig, r);
				} catch (DuplicateMethodException e1) {
					defaultMethodSpecs.put(e, MethodSpec.ERROR);
					processingEnv.getMessager().printMessage(Kind.ERROR, sig + " is already declared in module " + e1.getModule().getName() + ".", e);
				} catch (Group.DuplicateMethodException e2) {
					defaultMethodSpecs.put(e, MethodSpec.ERROR);
					processingEnv.getMessager().printMessage(Kind.ERROR, sig + " is already a " + 
							e2.getKind().toString().toLowerCase() + " in group " + e2.getGroup().getName() + ".", e);
				}
			}		
		}

		@Override
		protected MethodSpec getLabel(Element e) {
			return defaultMethodSpecs.get(e);
		}

		@Override
		public MethodSpec visitPackage(PackageElement e, Object _) {
			return defaultAnnotation();
		}
		private MethodSpec defaultAnnotation() {
			return Module.DEFAULT_INLINE ? MethodSpec.INLINE : MethodSpec.NONCOMM;
		}
	}

}