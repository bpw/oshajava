package oshajava.spec;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.JavaFileManager.Location;

import oshajava.util.ColdStorage;

public class SpecFileManager<T extends SpecFile> implements Iterable<T> {
	
	static interface Creator<U> {
		public U create(String qualifiedName);
	}

	private static final String DUMMY_EXT = ".dummy";
	private final String ext;
	private final ProcessingEnvironment env;
	private final Location base;
	private final Creator<T> creator;
	private final Map<String,T> items = new HashMap<String,T>();
	private final Map<T,File> files = new HashMap<T,File>();
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
	
	@SuppressWarnings("unchecked")
	public T getOrCreate(final String qualifiedName) throws IOException {
		if (items.containsKey(qualifiedName)) {
			return items.get(qualifiedName);
		}
		// If the resource is not yet loaded.
		T t;
		final File f = getFile(qualifiedName);
		if (!overwrite && f.exists()) {
			try {
				t = (T)ColdStorage.load(f);
			} catch (ClassNotFoundException e) {
				env.getMessager().printMessage(Kind.WARNING, qualifiedName + ext + " was stored in an outdated format. Generating a new (blank) version.");
				t = creator.create(qualifiedName);
				ColdStorage.store(f, t);
			}
		} else {
			t = creator.create(qualifiedName);
			ColdStorage.store(f, t);
		}
		items.put(qualifiedName, t);
		files.put(t, f);
		return t;
	}

	public T create(final String qualifiedName) throws IOException {
		T t = creator.create(qualifiedName);
		create(t);
		return t;
	}
	public void create(T t) throws IOException {
		if (files.containsKey(t)) {
			throw new IllegalStateException(t.getName() + ext + " is already registered with the SpecFileManager.");
		} else {
			final File f = getFile(t.getName());
			if (!overwrite && f.exists()) {
				throw new IllegalStateException(t.getName() + ext + " exists in the filesystem.");				
			} else {
				items.put(t.getName(), t);
				files.put(t, f);
				flush(t);
			}
		}
	}
	
	public void flushAll() throws IOException {
		for (final T t : files.keySet()) {
			flush(t);
		}
	}
	public void flush(final String qualifiedName) throws IOException {
		if (!items.containsKey(qualifiedName)) {
			throw new IllegalStateException(qualifiedName + ext + " has not been registered with the SpecFileManager.");
		}
		flush(items.get(qualifiedName));
	}
	public void flush(final T t) throws IOException {
		if (!files.containsKey(t)) {
			throw new IllegalStateException(t.getName() + ext + " has not been registered with the SpecFileManager.");
		}
		ColdStorage.store(files.get(t), t);
	}
	
	private File getFile(String qualifiedName) {
		final int lastDot = qualifiedName.lastIndexOf('.');
		final String pkg = lastDot == -1 ? "" : qualifiedName.substring(0, lastDot);
		final String simpleName = lastDot == -1 ? qualifiedName : qualifiedName.substring(lastDot + 1);
		FileObject f;
		try {
			f = env.getFiler().createResource(base, pkg, simpleName + ext + DUMMY_EXT);
		} catch (IOException e) {
			try {
				f = env.getFiler().getResource(base, pkg, simpleName + ext + DUMMY_EXT);
			} catch (IOException e1) {
				env.getMessager().printMessage(Kind.ERROR, "Cannot access " + qualifiedName + ext + " on filesystem.");
				throw new RuntimeException(e1);
			}
		}
		final String path = f.toUri().getPath();
		f.delete();
		return new File(path.substring(0, path.lastIndexOf(DUMMY_EXT)));
	}
	
	public int size() {
		return items.size();
	}
	
	public Iterator<T> iterator() {
		return items.values().iterator();
	}

}
