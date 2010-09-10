package oshajava.sourceinfo;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.URI;
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
	private final ModuleTraversal moduleScanner = new ModuleTraversal();
	private final GroupTraversal groupScanner = new GroupTraversal();
	private final CommTraversal groupMembershipScanner = new CommTraversal();
	
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
				moduleScanner.traverse(e);
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
					// Parent is unlabeled: traverse from parent.
					traverse(parent);
				} else {
					handle(e);
					// Traverse children.
					for (Element child : e.getEnclosedElements()) {
						traverse(child);
					}
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

	
	class ModuleTraversal extends LabelingTraversal<ModuleSpecBuilder> {

		@Override
		protected ModuleSpecBuilder getLabelFromAnnotation(Element e) {
			final Module memberDecl = e.getAnnotation(Module.class);
			if (memberDecl != null) {
				return getModuleByName(memberDecl.value());
			} else {
				return null;
			}
		}

		@Override
		protected void setLabel(Element e, ModuleSpecBuilder r) {
			processedModules.put(e, r);
		}

		@Override
		protected ModuleSpecBuilder getLabel(Element e) {
			return e == null ? null : processedModules.get(e);
		}

		@Override
		public ModuleSpecBuilder visitPackage(PackageElement e, Object _) {
			return getModuleByName(e.isUnnamed() ? ModuleSpec.DEFAULT_NAME : e.getQualifiedName() + "." + ModuleSpec.DEFAULT_NAME);
		}

	}
	
	class GroupTraversal extends Traversal {
		private final HashSet<Element> done = new HashSet<Element>();

		@Override
		protected boolean done(Element e) {
			return done.contains(e);
		}

		@Override
		protected void handle(Element e) {
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
			done.add(e);
		}

	}

	enum Comm { INLINE, NONCOMM, COMM, ERROR, DEFAULT };
	class CommTraversal extends LabelingTraversal<CommTraversal.MethodSpec> {
		// TODO: singletons for INLINE, NONCOMM
		// TODO different subclasses for inline, noncomm, comm.  Merge method via dynamic dispatch + static overloading/casts.
//		final InlineTag inlineTag = new InlineTag();
//		final NonCommTag noncommTag = new NonCommTag();
//	
//		abstract class CommTag {
//			
//		}
//		
//		class InlineTag extends CommTag { }
//		class NonCommTag { }
//		
//		class RWTag {
//			final Set<ModuleSpecBuilder.Group> readGroups = new HashSet<ModuleSpecBuilder.Group>(), writeGroups = new HashSet<ModuleSpecBuilder.Group>();
//		}
		
		class MethodSpec {
			final Inline inline;
			final NonComm noncomm;
			final Reader reader;
			final Writer writer;
			final Comm comm;
			public MethodSpec(Inline inline, NonComm noncomm, Reader reader, Writer writer) {
				this.reader = reader;
				this.writer = writer;
				this.inline = inline;
				this.noncomm = noncomm;
				if (inline != null) {
					if (noncomm == null && reader == null && writer == null) {
						comm = Comm.INLINE;
					} else {
						comm = Comm.ERROR;
					}
				} else if (noncomm != null) {
					if (reader == null && writer == null) {
						comm = Comm.NONCOMM;
					} else {
						comm = Comm.ERROR;
					}
				} else if (reader != null || writer != null) {
					comm = Comm.COMM;
				} else {
					comm = Comm.DEFAULT;
				}
			}
			public MethodSpec(Comm c) {
				inline = null;
				noncomm = null;
				reader = null;
				writer = null;
				comm = c;
			}
			public String toString() {
				return "" + inline + noncomm + reader + writer + "    " + comm;
			}
		}

		private final Map<Element, MethodSpec> defaultMethodSpecs = new HashMap<Element, MethodSpec>();

		@Override
		protected MethodSpec getLabelFromAnnotation(Element e) {
			MethodSpec ms = new MethodSpec(e.getAnnotation(Inline.class), e.getAnnotation(NonComm.class),
					e.getAnnotation(Reader.class), e.getAnnotation(Writer.class));
			if (ms.comm == Comm.ERROR) {
				error(e.getSimpleName() + ": conflicting annotations. @Inline, @NonComm, and @Writer/@Reader are mutually exclusive.");
			}
			return ms;
		}

		@Override
		protected MethodSpec mergeLabels(MethodSpec label, MethodSpec parentLabel) {
			if (label == null) return parentLabel;
			if (parentLabel == null) return label;
			switch (label.comm) {
			case INLINE:
			case NONCOMM:
				return label;
			case COMM:
				if ((parentLabel.reader != null && label.reader == null) || (parentLabel.writer != null && label.writer == null)) {
					return new MethodSpec(null, null, label.reader != null ? label.reader : parentLabel.reader,
							label.writer != null ? label.writer : parentLabel.writer);
				} else {
					return label;
				}
			case DEFAULT:
				return defaultAnnotation();
			default:
				return null;
			}
		}

		@Override
		protected void setLabel(Element e, MethodSpec r) {
			defaultMethodSpecs.put(e, r);
			if (e instanceof ExecutableElement) {
				final ExecutableElement m = (ExecutableElement)e;
				// Get the right module.
				final ModuleSpecBuilder module = processedModules.get(m);
				TypeElement cls = (TypeElement)e.getEnclosingElement();
				String sig = DescriptorWrangler.methodDescriptor(cls, m);

				boolean nonEmptyGroups = false;
				// Group membership.
				if (r.reader != null) {
					for (String groupId : r.reader.value()) {
						module.addReader(groupId, sig);
						module.ctrGroupMembership++;
					}
					nonEmptyGroups = r.reader.value() != null && r.reader.value().length > 0;
				}
				if (r.writer != null) {
					for (String groupId : r.writer.value()) {
						module.addWriter(groupId, sig);
						module.ctrGroupMembership++;
					}
					nonEmptyGroups = nonEmptyGroups || (r.writer.value() != null && r.writer.value().length > 0);
				}

				// Explicit Non-comm
				if (r.noncomm != null || ((r.writer != null || r.reader != null) && !nonEmptyGroups)) {
					module.addNonComm(sig);
				}

				// Default (unannotated)
				if (r.reader == null && r.writer == null && r.noncomm == null && r.inline == null) {
					module.addUnannotatedMethod(sig);
				}

				// Explicit Inline
				if (r.inline != null) {
					module.inlineMethod(sig);
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
			return ModuleSpecBuilder.DEFAULT_INLINE ? new MethodSpec(Comm.INLINE) : new MethodSpec(Comm.NONCOMM);
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
	public void error(String message) {
		processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message);
	}
	public void note(String message) {
		processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, message);
	}

}