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
import java.util.Collections;
import java.util.Set;


public class MethodSpec implements Serializable {
	private static final long serialVersionUID = 1L;

	public enum Kind { INLINE, NONCOMM, COMM, ERROR };
	private final Set<Group> readGroups, writeGroups;
	private final Kind kind;
	public MethodSpec(Kind kind) {
		this(kind, null, null);
	}
	public MethodSpec(Kind kind, Set<Group> readGroups, Set<Group> writeGroups) {
		this.kind = kind;
		this.readGroups = readGroups;
		this.writeGroups = writeGroups;
	}
	
	public Kind kind() { return kind; }
	public Set<Group> readGroups() { return readGroups  == null ? null : Collections.unmodifiableSet(readGroups); }
	public Set<Group> writeGroups() { return writeGroups  == null ? null : Collections.unmodifiableSet(writeGroups); }
	
	public void removeGroup(Group g) {
		if (readGroups != null) readGroups.remove(g);
		if (writeGroups != null) writeGroups.remove(g);
	}
	
	public static final MethodSpec INLINE = new MethodSpec(Kind.INLINE), NONCOMM = new MethodSpec(Kind.NONCOMM), 
		ERROR = new MethodSpec(Kind.ERROR);
	public static MethodSpec DEFAULT = NONCOMM;
	
	public String toString() {
		switch (kind) {
		case INLINE:
			return "@Inline";
		case NONCOMM:
			return "@NonComm";
		case COMM:
			return (readGroups == null ? "" : "@Reader(" + readGroups + ")") + (writeGroups == null ? "" : "@Writer(" + writeGroups + ")");
		case ERROR:
		default:
			return "Malformed";
		}
	}
}

