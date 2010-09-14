package oshajava.spec;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.JavaFileManager.Location;

import oshajava.util.ColdStorage;

public class SpecFileManager<T extends SpecFile> implements Iterable<T> {
	
	static interface Creator<U> {
		public U create(String qualifiedName);
	}

	private final String ext;
	private final ProcessingEnvironment env;
	private final Location base;
	private final Creator<T> creator;
	private final Map<String,T> items = new HashMap<String,T>();
	private final Map<T,FileObject> files = new HashMap<T,FileObject>();
	private final boolean overwrite;

	public SpecFileManager(final String ext, final ProcessingEnvironment env, final Location base) {
		this(ext, null, env, base, true);
	}
	public SpecFileManager(final String ext, final Creator<T> creator, final ProcessingEnvironment env, final Location base, final boolean overwrite) {
		this.ext = ext;
		this.env = env;
		this.base = base;
		this.creator = creator;
		this.overwrite = overwrite;
	}
	
	public void save(T t) {
		save(t, null);
	}
	public void save(T t, Element cause) {
		final String qualifiedName = t.getName();
		items.put(qualifiedName, t);
		final int lastDot = qualifiedName.lastIndexOf('.');
		final String pkg = lastDot == -1 ? "" : qualifiedName.substring(0, lastDot);
		final String simpleName = lastDot == -1 ? qualifiedName : qualifiedName.substring(lastDot + 1);
		try {
			FileObject f = env.getFiler().createResource(base, pkg, simpleName + ext, cause);
			files.put(t, f);
			ColdStorage.store(f, t);
		} catch (IOException e) {
			env.getMessager().printMessage(Kind.ERROR, qualifiedName + ext + " could not be created on disk.");
		}		
	}
	
	public T getOrCreate(final String qualifiedName) {
		return getOrCreate(qualifiedName, null);
	}
	@SuppressWarnings("unchecked")
	public T getOrCreate(final String qualifiedName, final Element cause) {
		if (items.containsKey(qualifiedName)) {
			return items.get(qualifiedName);
		}
		// If the resource is not yet loaded.
		T t;
		final int lastDot = qualifiedName.lastIndexOf('.');
		final String pkg = lastDot == -1 ? "" : qualifiedName.substring(0, lastDot);
		final String simpleName = lastDot == -1 ? qualifiedName : qualifiedName.substring(lastDot + 1);
		if (!overwrite) {
			try {
				// If a file for this resource already exists, this will succeed.
				t = (T)ColdStorage.load(env.getFiler().getResource(base, pkg, simpleName + ext));
				items.put(qualifiedName, t);
				return t;
			} catch (IOException e) {
				// Do nothing.  Just fail over. 
			} catch (ClassNotFoundException e) {
				env.getMessager().printMessage(Kind.WARNING, qualifiedName + ext + " stored in outdated format on disk. Generating a new (blank) version.");
			}
		}
		// Else we'll create a new resource and a new file for it.
		t = creator != null ? creator.create(qualifiedName) : null;
		try {
			files.put(t, env.getFiler().createResource(base, pkg, simpleName + ext, cause));
		} catch (IOException e) {
			env.getMessager().printMessage(Kind.ERROR, qualifiedName + ext + " could not be created on disk.");
		}
		items.put(qualifiedName, t);
		return t;
	}
	
	public T create(final String qualifiedName) {
		return create(qualifiedName, null);
	}
	public T create(final String qualifiedName, Element cause) {
		T t = creator.create(qualifiedName);
		save(t, cause);
		return t;
	}
	
	public void flushAll() throws IOException {
		for (final T t : files.keySet()) {
			flush(t);
		}
	}
	public void flush(final String qualifiedName) throws IOException {
		flush(items.get(qualifiedName));
	}
	public void flush(final T t) throws IOException {
		ColdStorage.store(files.get(t), t);
	}
	
	public int size() {
		return items.size();
	}
	
	public Iterator<T> iterator() {
		return items.values().iterator();
	}

}
