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

public class ArrayTypeDescriptor extends TypeDescriptor {
	private static final long serialVersionUID = 1L;
//	public static final ArrayTypeDescriptor STRING_ARRAY = new ArrayTypeDescriptor(TypeDescriptor.ofClass("java.lang.String"), 1);
	
	protected final TypeDescriptor elementType;
	protected final int arrayDepth;
	
	@Override
	public int hashCode() {
		return elementType.hashCode() ^ arrayDepth;
	}
	
	@Override
	public boolean equals(Object other) {
		if (other == this) return true;
		return other instanceof ArrayTypeDescriptor && 
			elementType.equals(((ArrayTypeDescriptor)other).elementType) && arrayDepth == ((ArrayTypeDescriptor)other).arrayDepth;
	}
	
	protected ArrayTypeDescriptor(TypeDescriptor elementType, int arrayDepth) {
		this.elementType = elementType;
		this.arrayDepth = arrayDepth;
	}
	
	private String sourceBrackets() {
		String out = "";
		for (int i = 0; i < arrayDepth; i++) {
			out += "[]";
		}
		return out;
	}
	
	private String internalBrackets() {
		String out = "";
		for (int i = 0; i < arrayDepth; i++) {
			out += "[";
		}
		return out;
	}
	
	public String getSourceName() {
		return elementType.getSourceName() + sourceBrackets();
	}

	public String getInternalName() {
		return elementType.getInternalName() + sourceBrackets();
	}
	
	public String getSourceDescriptor() {
		return getSourceName();
	}
	
	public String getInternalDescriptor() {
		return internalBrackets() + elementType.getInternalDescriptor();
	}
}
