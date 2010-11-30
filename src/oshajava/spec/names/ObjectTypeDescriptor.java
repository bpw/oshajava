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

import oshajava.support.acme.util.Assert;


public class ObjectTypeDescriptor extends TypeDescriptor {
	private static final long serialVersionUID = 1L;
	
	protected final CanonicalName typeName;
	/**
	 * NOTE: perhaps a dangerous decision, but the outer type will not be considered in hashCode or equals because
	 * if two descriptors have diff. outer types, but the same name, then there is a name collision anyway...
	 */
	protected transient ObjectTypeDescriptor outerType;
	
	protected transient ObjectTypeDescriptor superType = TypeDescriptor.OBJECT;
	
	protected transient boolean isStatic = true;
	
	@Override
	public int hashCode() {
		return typeName.hashCode();
	}
	
	@Override
	public boolean equals(Object other) {
		if (other == this) return true;
		return other instanceof ObjectTypeDescriptor && typeName.equals(((ObjectTypeDescriptor)other).typeName);
	}
	
	protected ObjectTypeDescriptor(CanonicalName typeName) {
		Assert.assertTrue(typeName != null);
		this.typeName = typeName;
	}
	
	public CanonicalName getTypeName() {
		return typeName;
	}
	
	public ObjectTypeDescriptor getOuterType() {
		return outerType;
	}
	
	public void setOuterType(ObjectTypeDescriptor outerType, boolean innerIsStatic) {
		Assert.assertTrue(this.outerType == null);
		this.outerType = outerType;
		this.isStatic = innerIsStatic;
	}
	
	public ObjectTypeDescriptor getSuperType() {
		return superType;
	}
	
	public void setSuperType(ObjectTypeDescriptor superType) {
		Assert.assertTrue(this.superType == TypeDescriptor.OBJECT);
		this.superType = superType;
	}
	
	// TODO public boolean isAnonymous() { ...
	
	public boolean isStatic() {
		return isStatic();
	}
	
	public String getSourceName() {
		return typeName.getSourceName();
	}

	public String getInternalName() {
		return typeName.getInternalName();
	}
	
	public String getSourceDescriptor() {
		return getSourceName();
	}

	public String getInternalDescriptor() {
		return "L" + getInternalName() + ";";
	}
	
	public boolean isInner() {
		return outerType != null;
	}
	
}

