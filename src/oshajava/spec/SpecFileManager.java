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

import oshajava.spec.names.CanonicalName;
import oshajava.util.ColdStorage;

public class SpecFileManager<T extends SpecFile> implements Iterable<T> {
	
	static interface Creator<U> {
		public U create(CanonicalName name);
	}

	private static final String DUMMY_EXT = ".dummy";
	private final String ext;
	private final ProcessingEnvironment env;
	private final Location base;
	private final Creator<T> creator;
	private final Map<CanonicalName,T> items = new HashMap<CanonicalName,T>();
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
	public T getOrCreate(final CanonicalName name) throws IOException {
		if (items.containsKey(name)) {
			return items.get(name);
		}
		// If the resource is not yet loaded.
		T t;
		final File f = getFile(name);
		if (!overwrite && f.exists()) {
			try {
				t = (T)ColdStorage.load(f);
			} catch (ClassNotFoundException e) {
				env.getMessager().printMessage(Kind.WARNING, name + ext + " was stored in an outdated format. Generating a new (blank) version.");
				t = creator.create(name);
				ColdStorage.store(f, t);
			}
		} else {
			t = creator.create(name);
			ColdStorage.store(f, t);
		}
		items.put(name, t);
		files.put(t, f);
		return t;
	}

	public T create(final CanonicalName name) throws IOException {
		T t = creator.create(name);
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
	public void flush(final CanonicalName name) throws IOException {
		if (!items.containsKey(name)) {
			throw new IllegalStateException(name + ext + " has not been registered with the SpecFileManager.");
		}
		flush(items.get(name));
	}
	public void flush(final T t) throws IOException {
		if (!files.containsKey(t)) {
			throw new IllegalStateException(t.getName() + ext + " has not been registered with the SpecFileManager.");
		}
		ColdStorage.store(files.get(t), t);
	}
	
	private File getFile(CanonicalName name) {
//		System.out.println("    " + name + "    " + name.getPackage() + "    " + name.getSimpleName());
		FileObject f;
		try {
			f = env.getFiler().createResource(base, name.getSourcePackage(), name.getSimpleName() + ext + DUMMY_EXT);
		} catch (IOException e) {
			try {
				f = env.getFiler().getResource(base, name.getSourcePackage(), name.getSimpleName() + ext + DUMMY_EXT);
			} catch (IOException e1) {
				env.getMessager().printMessage(Kind.ERROR, "Cannot access " + name + ext + " on filesystem.");
				throw new RuntimeException(e1);
			}
		}
		final String path = f.toUri().getPath();
		f.delete();
		return new File(path.substring(0, path.lastIndexOf(DUMMY_EXT))).getAbsoluteFile();
	}
	
	public int size() {
		return items.size();
	}
	
	public Iterator<T> iterator() {
		return items.values().iterator();
	}

}
