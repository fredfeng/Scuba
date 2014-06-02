package framework.scuba.domain;

import joeq.Class.jq_Class;
import joeq.Class.jq_Field;

public class StaticElem extends StackObject {

	// the class this static field is declared in
	protected jq_Class clazz;

	// the field this static element denotes
	protected jq_Field field;

	public StaticElem(jq_Class clazz, jq_Field field) {
		this.clazz = clazz;
		this.field = field;
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
		return "[SC]" + clazz.getName();
	}

	@Override
	public boolean hasFieldSelector(FieldElem field) {
		return false;
	}
}
