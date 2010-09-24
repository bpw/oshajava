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

