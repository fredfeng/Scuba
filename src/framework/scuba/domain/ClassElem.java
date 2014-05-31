package framework.scuba.domain;

import joeq.Class.jq_Class;

public class ClassElem extends HeapObject {

	// the class this belongs to
	protected jq_Class clazz;

	public ClassElem(jq_Class clazz) {
		this.clazz = clazz;
	}

	@Override
	public AbstractMemLoc findRoot() {
		return this;
	}

	@Override
	public boolean equals(Object other) {
		return (other instanceof ClassElem)
				&& (clazz.equals(((ClassElem) other).clazz));
	}

	@Override
	public int hashCode() {
		return clazz.hashCode();
	}

	@Override
	public String toString() {
		return "[Class] " + clazz;
	}

}
