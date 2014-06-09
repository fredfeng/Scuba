package framework.scuba.domain;

import joeq.Class.jq_Field;

public class NormalFieldElem extends FieldElem {
	// the class this field belongs to
	// protected jq_Class clazz;

	// the field this represents
	protected jq_Field field;

	public NormalFieldElem(jq_Field field) {
		this.field = field;
	}

	// getField method
	public jq_Field getField() {
		return this.field;
	}

	@Override
	public boolean equals(Object other) {
		return (other instanceof NormalFieldElem)
				&& (this.field.equals(((NormalFieldElem) other).field));
	}

	@Override
	public int hashCode() {
		return field.hashCode();
	}

	@Override
	public String toString() {
		return "[F]" + field;
	}
}
