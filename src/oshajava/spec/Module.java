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

import java.util.HashMap;
import java.util.Map;

import oshajava.spec.names.CanonicalName;
import oshajava.spec.names.MethodDescriptor;
import oshajava.support.acme.util.Assert;

/**
 * A compile-time representation of a module.
 */
public class Module extends SpecFile {
	
	private static final long serialVersionUID = 3L;

	public static final String EXT = ".omi"; // Osha Module Incremental
	
	/****************************/
	
	@SuppressWarnings("serial")
	static class DuplicateGroupException extends Exception {
		protected final Module module;
		protected final Group sig;
		public DuplicateGroupException(Module module, Group group) {
			super((group.kind() == Group.Kind.COMM ? "Communication" : "Interface") + " Group \"" + group + "\" is already entered in module " + module + ".");
			this.module = module;
			this.sig = group;
		}
	}
	
	@SuppressWarnings("serial")
	static class DuplicateMethodException extends Exception {
		protected final Module module;
		protected final MethodDescriptor sig;
		public DuplicateMethodException(Module module, MethodDescriptor sig) {
			super("Method " + sig + " is already entered in module " + module + ".");
			this.module = module;
			this.sig = sig;
		}
		public MethodDescriptor getMethod() {
			return sig;
		}
		public Module getModule() {
			return module;
		}
	}
	
	@SuppressWarnings("serial")
	static class GroupNotFoundException extends Exception {
		protected final String group;
		public GroupNotFoundException(String group) {
			super("Group \"" + group + "\" not found.");
			this.group = group;
		}
	}
	
	protected Map<MethodDescriptor,MethodSpec> methodSpecs = new HashMap<MethodDescriptor,MethodSpec>();
	protected Map<String,Group> groups = new HashMap<String,Group>();
	protected int numCommMethods = 0, numNoncommMethods = 0, numInlinedMethods = 0, numCommGroups = 0, numIfaceGroups = 0;
	
	public Module(CanonicalName qualifiedName) {
		super(qualifiedName);
	}
	
	/**
	 * Add a communication group to the method.
	 * @param group Group name
	 * @throws DuplicateGroupException if a group (communication or interface) with the same name already exists in this module.
	 */
	public void addCommGroup(String group) throws DuplicateGroupException {
		if (groups.containsKey(group)) {
			throw new DuplicateGroupException(this, groups.get(group));
		}
		groups.put(group, new Group(Group.Kind.COMM, group));
		numCommGroups++;
	}
	
	/**
	 * Add an interface group to the method.
	 * @param group Group name
	 * @throws DuplicateGroupException if a group (communication or interface) with the same name already exists in this module.
	 */
	public void addInterfaceGroup(String group) throws DuplicateGroupException {
		if (groups.containsKey(group)) {
			throw new DuplicateGroupException(this, groups.get(group));
		}
		groups.put(group, new Group(Group.Kind.INTERFACE, group));
		numIfaceGroups++;
	}
	
	/**
	 * Retrieve the named group.
	 * @param group Name of the group to retrieve.
	 * @return The Group.
	 * @throws GroupNotFoundException if no group with this name is found in this module.
	 */
	public Group getGroup(String group) throws GroupNotFoundException {
		if (!groups.containsKey(group)) {
			throw new GroupNotFoundException(group);
		}
		return groups.get(group);
	}
	
	/**
	 * Add a method with the given individual specification to the module.
	 * @param sig Method signature
	 * @param spec MethodSpec for the method.
	 * @throws DuplicateMethodException if a method with the same signature already exists in this module.
	 * @throws Group.DuplicateMethodException 
	 */
	public void addMethod(MethodDescriptor sig, MethodSpec spec) throws DuplicateMethodException, Group.DuplicateMethodException {
		if (methodSpecs.containsKey(sig)) {
			throw new DuplicateMethodException(this, sig);
		}
		methodSpecs.put(sig, spec);
		switch (spec.kind()) {
		case COMM:
			numCommMethods++;
			final Iterable<Group> readGroups = spec.readGroups();
			if (readGroups != null) {
				for (Group g : spec.readGroups()) {
					g.addReader(sig);
				}
			}
			final Iterable<Group> writeGroups = spec.writeGroups();
			if (writeGroups != null) {
				for (Group g : spec.writeGroups()) {
					g.addWriter(sig);
				}
			}
			Assert.assertTrue(readGroups != null || writeGroups != null, "Noncomm method %s not marked as comm.", sig);
			break;
		case INLINE:
			numInlinedMethods++;
			break;
		case NONCOMM:
			numNoncommMethods++;
			break;
		case ERROR:
		default:
			Assert.fail("Bad method spec added to module.");
		}
	}
	
	public void removeMethod(String sig) {
		if (methodSpecs.containsKey(sig)) {
			MethodSpec spec = methodSpecs.get(sig);
			final Iterable<Group> readGroups = spec.readGroups();
			if (readGroups != null) {
				for (Group g : spec.readGroups()) {
					g.removeMethod(sig);
				}
			}
			final Iterable<Group> writeGroups = spec.writeGroups();
			if (writeGroups != null) {
				for (Group g : spec.writeGroups()) {
					g.removeMethod(sig);
				}
			}
		}
	}
	
	public CompiledModuleSpec compile() {
		return new CompiledModuleSpec(getName(), methodSpecs);
	}
	
	public String toString() {
		String out = "Module " + getName() + "\n";
		out += "  Methods: " + methodSpecs.size() + "\n";
		out += "    Communicating: " + numCommMethods + "\n";
		for (Map.Entry<MethodDescriptor, MethodSpec> e : methodSpecs.entrySet()) {
			if (e.getValue().kind() == MethodSpec.Kind.COMM) {
				out += "      " + e.getKey() + "\n";
				out += "        Read groups: ";
				{
					final Iterable<Group> readers = e.getValue().readGroups();
					if (readers != null) {
						for (Group g : readers) {
							out += g.getName() + "  ";
						}
					}
				}
				out += "\n";
				out += "        Write groups: ";
				{
					final Iterable<Group> writers = e.getValue().writeGroups();
					if (writers != null) {
						for (Group g : writers) {
							out += g.getName() + "  ";
						}
					}
				}
				out += "\n";
			}
		}
		out += "    Non-communicating: " + numNoncommMethods + "\n";
		for (Map.Entry<MethodDescriptor, MethodSpec> e : methodSpecs.entrySet()) {
			if (e.getValue().kind() == MethodSpec.Kind.NONCOMM) {
				out += "      " + e.getKey() + "\n";
			}
		}
		out += "    Inlined: " + numInlinedMethods + "\n";
		for (Map.Entry<MethodDescriptor, MethodSpec> e : methodSpecs.entrySet()) {
			if (e.getValue().kind() == MethodSpec.Kind.INLINE) {
				out += "      " + e.getKey() + "\n";
			}
		}
		out += "  Communication Groups: " + numCommGroups + "\n";
		for (Map.Entry<String, Group> e : groups.entrySet()) {
			if (e.getValue().kind() == Group.Kind.COMM) {
				out += "      " + e.getKey() + "\n";
				out += "        Readers: \n";
				{
					final Iterable<MethodDescriptor> readers = e.getValue().readers();
					if (readers != null) {
						for (MethodDescriptor g : readers) {
							out += "          " + g + "\n";
						}
					}
				}
				out += "        Writers:\n";
				{
					final Iterable<MethodDescriptor> writers = e.getValue().writers();
					if (writers != null) {
						for (MethodDescriptor g : writers) {
							out += "          " + g + "\n";
						}
					}
				}
			}
		}
		out += "  Interface Groups: " + numIfaceGroups + "\n";
		for (Map.Entry<String, Group> e : groups.entrySet()) {
			if (e.getValue().kind() == Group.Kind.INTERFACE) {
				out += "      " + e.getKey() + "\n";
				out += "        Readers:\n";
				{
					final Iterable<MethodDescriptor> readers = e.getValue().readers();
					if (readers != null) {
						for (MethodDescriptor g : readers) {
							out += "          " + g + "\n";
						}
					}
				}
				out += "        Writers:\n";
				{
					final Iterable<MethodDescriptor> writers = e.getValue().writers();
					if (writers != null) {
						for (MethodDescriptor g : writers) {
							out += "          " + g + "\n";
						}
					}
				}
			}
		}
		return out;
	}

	
}
