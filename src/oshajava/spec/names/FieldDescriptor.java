package oshajava.spec.names;

import java.util.HashMap;
import java.util.Map;

public class FieldDescriptor extends Descriptor {

	private static final long serialVersionUID = 1L;
	
	protected final ObjectTypeDescriptor declaringType;
	protected final String fieldName;
	protected final TypeDescriptor fieldType;
	
	private FieldDescriptor(ObjectTypeDescriptor containingType, String fieldName, TypeDescriptor fieldType) {
		this.declaringType = containingType;
		this.fieldName = fieldName;
		this.fieldType = fieldType;
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
		String s = fieldType.getSourceDescriptor() + ' ' + declaringType.getSourceName() + '.' + fieldName;
		if (stringToDescriptor.containsKey(s)) {
			return stringToDescriptor.get(s);
		} else {
			FieldDescriptor fd = new FieldDescriptor(declaringType, fieldName, fieldType);
			stringToDescriptor.put(s, fd);
			return fd;
		}
	}

}
