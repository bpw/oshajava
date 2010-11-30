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

import java.util.HashMap;
import java.util.Map;

import oshajava.support.org.objectweb.asm.Opcodes;

public class FieldDescriptor extends Descriptor {

	private static final long serialVersionUID = 1L;
	
	protected final ObjectTypeDescriptor declaringType;
	protected final String fieldName;
	protected final TypeDescriptor fieldType;
	protected int access;
	
	private FieldDescriptor(ObjectTypeDescriptor containingType, String fieldName, TypeDescriptor fieldType, int access) {
		this.declaringType = containingType;
		this.fieldName = fieldName;
		this.fieldType = fieldType;
		this.access = access;
	}
	
	private void setAccess(int access) {
		this.access = access;
	}
	
	public ObjectTypeDescriptor getDeclaringType() {
		return declaringType;
	}

	public String getFieldName() {
		return fieldName;
	}

	public TypeDescriptor getFieldType() {
		return fieldType;
	}
	
	public int getAccessFlags() {
		return access;
	}
	
	public boolean isFinal() {
		return (access & Opcodes.ACC_FINAL) != 0;
	}
	
	public boolean isVolatile() {
		return (access & Opcodes.ACC_VOLATILE) != 0;
	}
	
	public boolean isStatic() {
		return (access & Opcodes.ACC_STATIC) != 0;
	}

	
	@Override
	public String getSourceDescriptor() {
		return fieldType.getSourceDescriptor() + ' ' + getSourceName();
	}

	@Override
	public String getInternalDescriptor() {
		return fieldType.getInternalDescriptor() + ' ' + getInternalName();
	}

	@Override
	public String getSourceName() {
		return declaringType.getSourceName() + '.' + fieldName;
	}

	@Override
	public String getInternalName() {
		return declaringType.getInternalName() + '.' + fieldName;
	}

	@Override
	public int hashCode() {
		return declaringType.hashCode() ^ fieldName.hashCode() ^ fieldType.hashCode();
	}

	@Override
	public boolean equals(Object other) {
		if (other == this) return true;
		if (other instanceof FieldDescriptor) {
			FieldDescriptor fd = (FieldDescriptor)other;
			return declaringType.equals(fd.declaringType) && fieldName.equals(fd.fieldName) && fieldType.equals(fd.fieldType);
		}
		return false;
	}

	// Runtime.
	private static final Map<String,FieldDescriptor> stringToDescriptor = new HashMap<String,FieldDescriptor>();
	public static FieldDescriptor of(ObjectTypeDescriptor declaringType, String fieldName, TypeDescriptor fieldType) {
		return of(declaringType, fieldName, fieldType, -1);
	}
	public static FieldDescriptor of(ObjectTypeDescriptor declaringType, String fieldName, TypeDescriptor fieldType, int access) {
		String s = fieldType.getSourceDescriptor() + ' ' + declaringType.getSourceName() + '.' + fieldName;
		if (stringToDescriptor.containsKey(s)) {
			final FieldDescriptor fd = stringToDescriptor.get(s);
			if (access != -1) {
				fd.setAccess(access);
			}
			return fd;
		} else {
			FieldDescriptor fd = new FieldDescriptor(declaringType, fieldName, fieldType, access);
			stringToDescriptor.put(s, fd);
			return fd;
		}
	}

}
