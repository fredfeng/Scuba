package framework.scuba.domain;

import joeq.Class.jq_Class;
import joeq.Class.jq_Field;

public class StaticElem extends HeapObject {

	// the class this belongs to
	protected jq_Class clazz;

	public StaticElem(jq_Class clazz) {
		this.clazz = clazz;
	}

	@Override
	public AbstractMemLoc findRoot() {
		return this;
	}

	@Override
	public boolean equals(Object other) {
		return (other instanceof StaticElem)
				&& (clazz.equals(((StaticElem) other).clazz));
	}

	@Override
	public int hashCode() {
		return clazz.hashCode();
	}

	@Override
	public String toString() {
		return "[Static Class] " + clazz;
	}

}
