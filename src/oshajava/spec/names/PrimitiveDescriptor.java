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

package oshajava.spec.names;


public class PrimitiveDescriptor extends TypeDescriptor {

	private static final long serialVersionUID = 1L;
	
	public static final PrimitiveDescriptor BOOLEAN = new PrimitiveDescriptor("boolean", "Z");
	public static final PrimitiveDescriptor BYTE    = new PrimitiveDescriptor("byte",    "B");
	public static final PrimitiveDescriptor CHAR    = new PrimitiveDescriptor("char",    "C");
	public static final PrimitiveDescriptor DOUBLE  = new PrimitiveDescriptor("double",  "D");
	public static final PrimitiveDescriptor FLOAT   = new PrimitiveDescriptor("float",   "F");
	public static final PrimitiveDescriptor INT     = new PrimitiveDescriptor("int",     "I");
	public static final PrimitiveDescriptor LONG    = new PrimitiveDescriptor("long",    "J");
	public static final PrimitiveDescriptor SHORT   = new PrimitiveDescriptor("short",   "S");
	public static final PrimitiveDescriptor VOID    = new PrimitiveDescriptor("void",    "V");
	
	private final String sourceDescriptor, internalDescriptor;
	
	/**
	 * Since the constructor is private, we check only against the internal descriptor...
	 */
	@Override
	public int hashCode() {
		return internalDescriptor.hashCode();
	}
	
	/**
	 * Since the constructor is private, we check only against the internal descriptor...
	 */
	@Override
	public boolean equals(Object other) {
		if (other == this) return true;
		return other instanceof PrimitiveDescriptor && internalDescriptor.equals(((PrimitiveDescriptor)other).internalDescriptor);
	}
	
	private PrimitiveDescriptor(String sourceDescriptor, String internalDescriptor) {
		this.internalDescriptor = internalDescriptor;
		this.sourceDescriptor = sourceDescriptor;
	}

	@Override
	public String getSourceName() {
		return sourceDescriptor;
	}

	@Override
	public String getInternalName() {
		return internalDescriptor;
	}

	@Override
	public String getSourceDescriptor() {
		return sourceDescriptor;
	}

	@Override
	public String getInternalDescriptor() {
		return internalDescriptor;
	}

}
