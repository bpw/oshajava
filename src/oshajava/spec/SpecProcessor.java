/*

Copyright (c) 2010, Benjamin P. Wood and Adrian Sampson, University of Washington
All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of the University of Washington nor the names of its
   contributors may be used to endorse or promote products derived from this
   software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

package oshajava.spec;

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
import oshajava.spec.Module.DuplicateGroupException;
import oshajava.spec.Module.DuplicateMethodException;
import oshajava.spec.SpecFileManager.Creator;
import oshajava.spec.names.CanonicalName;
import oshajava.spec.names.MethodDescriptor;
import oshajava.support.acme.util.Assert;

// TODO add an @Untracked annotation.  Applies to methods.  Maybe fields too.

@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_6)
@SupportedOptions({SpecProcessor.HELP_OPTION, SpecProcessor.DEFAULT_ANN_OPTION, SpecProcessor.DEBUG_OPTION})
public class SpecProcessor extends AbstractProcessor {
	
	public static final String DEFAULT_ANN_OPTION = "oshajava.annotation.default",
		DEBUG_OPTION = "oshajava.verbose",
		HELP_OPTION = "oshajava.help";
	public static final String DEFAULT_ANN_ENV_VAR = DEFAULT_ANN_OPTION.replace('.','_').toUpperCase(),
		DEBUG_ENV_VAR = "OSHAJAVA_VERBOSE",
		HELP_ENV_VAR = "OSHAJAVA_HELP";
	private static final String INLINE_ANN = "inline", NONCOMM_ANN = "noncomm";
	
	public static final String[][] OPTIONS = {
		{ HELP_OPTION, "Display this help message." },
		{ DEBUG_OPTION, "Print lots of information about the spec compilation process." },
		{ DEFAULT_ANN_OPTION, "One of [" + NONCOMM_ANN + ", " + INLINE_ANN + "]. Set the default annotation for methods." }
	};
	
	public static void usage() {
		System.out.println("oshajava.spec.SpecProcessor options");
		for (String[] option : OPTIONS) {
			System.out.println("  -A" + option[0] + "        " + option[1]);
		}
	}
	
	private static final Location OUTPUT = StandardLocation.SOURCE_OUTPUT;

	private SpecFileManager<Module> modules;
	private SpecFileManager<ModuleMap> maps;
	private SpecFileManager<CompiledModuleSpec> moduleSpecs;
	private final Map<Element, Module> processedModules = new HashMap<Element, Module>();
	private final ModuleTraversal moduleScanner = new ModuleTraversal();
	private final GroupTraversal groupScanner = new GroupTraversal();
	private final CommTraversal groupMembershipScanner = new CommTraversal();
	
	private boolean complete = false;
	
	private boolean justHelp = false;
	
	private boolean verbose;
	
	@Override
	public synchronized void init(ProcessingEnvironment env) {
		super.init(env);
//		note("oshajava $Revision$".replace("$Revision: ", "").replace(" $", ""));
		modules = new SpecFileManager<Module>(Module.EXT, new Creator<Module>() {
			public Module create(final CanonicalName qualifiedName) {
				return new Module(qualifiedName);
			}
		}, env, OUTPUT, false); // FIXME: Why source output and not class output?  
		// Cody switched to source output because something wasn't working.
		maps = new SpecFileManager<ModuleMap>(ModuleMap.EXT, new Creator<ModuleMap>() {
			public ModuleMap create(final CanonicalName className) {
				return new ModuleMap(className);
			}
		}, env, OUTPUT, true);
		moduleSpecs = new SpecFileManager<CompiledModuleSpec>(CompiledModuleSpec.EXT, env, OUTPUT);
		
		// Set the default annotation to @Inline or @NonComm.
		// e.g. -Aoshajava.annotation.default=noncomm
		String defaultAnn = env.getOptions().get(DEFAULT_ANN_OPTION);
		if (defaultAnn == null) {
			defaultAnn = System.getenv(DEFAULT_ANN_ENV_VAR);
		}
		if (defaultAnn == null) {
			// Do nothing. MethodSpec.DEFAULT is preset to the standard default.
		} else if (defaultAnn.toLowerCase().equals(NONCOMM_ANN)) {
			MethodSpec.DEFAULT = MethodSpec.NONCOMM;
		} else if (defaultAnn.toLowerCase().equals(INLINE_ANN)) {
			MethodSpec.DEFAULT = MethodSpec.INLINE;
		} else {
			throw new IllegalArgumentException(DEFAULT_ANN_OPTION + "=" + defaultAnn);
		}
		note("Default oshajava annotation set to " + (MethodSpec.DEFAULT == MethodSpec.NONCOMM ? "@NonComm" : "@Inline") + ".");
		verbose = env.getOptions().containsKey(DEBUG_OPTION) || System.getenv(DEBUG_ENV_VAR) != null;
		justHelp = env.getOptions().containsKey(HELP_OPTION) || System.getenv(HELP_ENV_VAR) != null;
	}

	/**
	 * Print an error and stop compiling.
	 */
	public void error(String message) {
		processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message);
	}
	public void error(String message, Element e) {
		processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, e);
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
		if (justHelp) return false;
		Assert.assertTrue(!complete, "Egads!");
		// Packages, classes, and interfaces to process in this round.
		final Set<? extends Element> elements = round.getRootElements();
		if (elements.isEmpty()) {
			complete = true;
			// If empty, then processing is done. Flush the spec files to disk.
			try {
				modules.flushAll();
				for (Module m : modules) {
					CompiledModuleSpec ms = m.compile();
					moduleSpecs.create(ms);
					if (verbose) {
						System.out.println(m);
						System.out.println(ms);
					}
				}
				maps.flushAll();
				note("Compiled " + modules.size() + " OSHA modules from " + maps.size() + " classes.");
			} catch (IOException e) {
				error("Could not write spec info to filesystem.");
				throw new RuntimeException(e);
			}
		} else {
			// First, establish modules.
			for (final Element e : elements) {
				moduleScanner.traverse(e);
			}
			// Second, establish groups.
			for (final Element e : elements) {
				groupScanner.traverse(e);
			}
			// TODO: Incremental: 
			// for modulemaps, for each class, remove from modules all groups previously declared in this class that are no longer declared in this class 
			// (or that now belong to a diff module).
			// Third, establish readers and writers in groups, noncomm, and inline.
			for (final Element e : elements) {
				groupMembershipScanner.traverse(e);
			}
		}
		// Let other processors see the annotations.
		return false;
	}
	
	private void assignModule(Element e, Module m) {
		Assert.assertTrue(e != null && m != null);
		Assert.assertTrue(!processedModules.containsKey(e), "Element %s has already been assigned to a module!", e.getSimpleName());
		processedModules.put(e, m);
	}
	
	private Module getAssignedModule(Element e) {
		Assert.assertTrue(e != null);
		Assert.assertTrue(processedModules.containsKey(e), "Element %s has not been assigned to a module!", e.getSimpleName());
		return uncheckedGetAssignedModule(e);
	}
	
	private Module uncheckedGetAssignedModule(Element e) {
		return processedModules.get(e);
	}
	
	abstract class Traversal {
		
		protected abstract boolean done(Element e);
		/**
		 * Invariant: when handle(e) is called, either parent == null or all of e's ancestors have been handled already.
		 * @param e
		 */
		protected abstract void handle(Element e);
		
		protected void traverseAncestors(Element e) {
			// If this element is not already done:
			final Element parent = e.getEnclosingElement();
			if (parent != null && !done(parent)) {
				traverseAncestors(parent);
				handle(parent);
			}
		}
		
		public void traverse(Element e) {
			if (!done(e)) {
				traverseAncestors(e);
				handle(e);
				// Traverse children.
				for (Element child : e.getEnclosedElements()) {
					if (child instanceof ExecutableElement || child instanceof PackageElement || child instanceof TypeElement) {
						traverse(child);
					}
				}
				postHandle(e);
			}
		}
		
		protected void postHandle(Element e) {}
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
				Module m;
				try {
					m = modules.getOrCreate(CanonicalName.of(memberDecl.value()));
				} catch (IOException e1) {
					error("Could not access module info on filesystem.");
					throw new RuntimeException(e1);
				}
				if (verbose) {
					System.out.println("Loaded " + m);
				}
				return m;
			} else {
				return null;
			}
		}

		@Override
		protected void setLabel(Element e, Module r) {
			assignModule(e, r);
			if (e instanceof ExecutableElement) {
				TypeElement cls = (TypeElement)e.getEnclosingElement();
				try {
					maps.getOrCreate(CanonicalName.of(cls, processingEnv.getElementUtils())).put(
							MethodDescriptor.of((ExecutableElement)e, processingEnv.getElementUtils()), getAssignedModule(e).getName());
				} catch (IOException ioe) {
					error("Could not write spec info to filesystem.");
					throw new RuntimeException(ioe);
				}
			}
		}

		@Override
		protected Module getLabel(Element e) {
			return e == null ? null : uncheckedGetAssignedModule(e);
		}

		@Override
		public Module visitPackage(PackageElement e, Object _) {
			Module m;
			try {
				m = modules.getOrCreate(CanonicalName.of((e.isUnnamed() ? "" : e.getQualifiedName().toString()), CompiledModuleSpec.DEFAULT_NAME));
			} catch (IOException e1) {
				error("Could not access module info on filesystem.");
				throw new RuntimeException(e1);
			}
			if (verbose) {
				System.out.println("Loaded " + m);
			}
			return m;

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
			final Module module = getAssignedModule(e);
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
			final Module module = getAssignedModule(e);
			MethodSpec ms = null;
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
					return null;
				}
				if (ms != null && ms.kind() == MethodSpec.Kind.ERROR) {
					String x = "";
					if (inline != null) x += inline + "  ";
					if (noncomm != null) x += noncomm + "  ";
					if (reader != null) x += reader + "  ";
					if (writer != null) x += writer + "  ";
					error("Conflicting annotations on " + e.getSimpleName() + ":  " + x, e);
				}
			} catch (Module.GroupNotFoundException e1) {
				ms = MethodSpec.ERROR;
				error("Group " + e1.group + " not found in module " + module.getName(), e);
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
			default:
				return MethodSpec.DEFAULT;
			}
		}

		@Override
		protected void setLabel(Element e, MethodSpec r) {
//			note("setLabel " + e.getSimpleName());
			defaultMethodSpecs.put(e, r);
			if (e instanceof ExecutableElement) {
				final ExecutableElement m = (ExecutableElement)e;
				// Get the right module.
				final Module module = getAssignedModule(m);
				MethodDescriptor sig = MethodDescriptor.of(m, processingEnv.getElementUtils());
//				Assert.assertTrue(module != null, "null module for method element %s", sig);
				if (verbose) {
//					System.out.println(sig + ": " + r);
				}
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
			return MethodSpec.DEFAULT;
		}
	}

}