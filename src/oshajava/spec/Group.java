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

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import oshajava.spec.names.MethodDescriptor;
import oshajava.support.acme.util.Assert;

public 	class Group implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	@SuppressWarnings("serial")
	static class DuplicateMethodException extends Exception {
		enum Kind { READER, WRITER };
		protected final Group group;
		protected final MethodDescriptor sig;
		protected final Kind kind;
		public DuplicateMethodException(Group group, MethodDescriptor sig, Kind kind) {
			super("Method " + sig + " is already entered in group " + group + ".");
			this.group = group;
			this.sig = sig;
			this.kind = kind;
		}
		public MethodDescriptor getMethod() {
			return sig;
		}
		public Group getGroup() {
			return group;
		}
		public Kind getKind() {
			return kind;
		}
	}
	
	enum Kind { COMM, INTERFACE };

	private final Kind kind;
	private final Set<MethodDescriptor> readers = new HashSet<MethodDescriptor>(), writers = new HashSet<MethodDescriptor>();
	private final String name;
	
	public Group(Kind kind, String name) {
		this.kind = kind;
		this.name = name;
	}
	
	public Kind kind() {
		return kind;
	}
	
	public String getName() {
		return name;
	}
	
	public Iterable<MethodDescriptor> readers() {
		return readers;
	}
	
	public Iterable<MethodDescriptor> writers() {
		return writers;
	}
	
	public void addReader(MethodDescriptor sig) throws DuplicateMethodException {
		Assert.assertTrue(sig != null, "Null method sig");
		if (readers.contains(sig)) {
			throw new DuplicateMethodException(this, sig, DuplicateMethodException.Kind.READER);
		}
		readers.add(sig);
	}
	
	public void addWriter(MethodDescriptor sig) throws DuplicateMethodException {
		Assert.assertTrue(sig != null, "Null method sig");
		if (writers.contains(sig)) {
			throw new DuplicateMethodException(this, sig, DuplicateMethodException.Kind.WRITER);
		}
		writers.add(sig);
	}
	
	public void removeMethod(String sig) {
		readers.remove(sig);
		writers.remove(sig);
	}
	
	public boolean isEmpty() {
		return readers.isEmpty() && writers.isEmpty();
	}
	
	public String toString() {
		return getName();
	}
}
