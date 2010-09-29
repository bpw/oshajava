package oshajava.spec.names;

import java.util.HashMap;
import java.util.Map;

import com.sun.xml.internal.ws.org.objectweb.asm.Opcodes;

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
