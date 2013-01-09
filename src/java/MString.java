package PAnnotator.java;

import clojure.lang.IPersistentMap;
import clojure.lang.IObj;

//a wrapper around the String class that supports Clojure maps as meta-data
public final class MString implements IObj {

	private final String value;
	private final IPersistentMap meta;

	public MString(IPersistentMap meta, String value) {
		this.meta = meta;
		this.value = value;
	}
	public MString(String value) {
		this(null, value);
	}
	
	public String getString(){
	        return value;
	}

	public IObj withMeta(IPersistentMap meta) {
		return new MString(meta, value);
	}

	public IPersistentMap meta() {
		return meta;
	}
}
