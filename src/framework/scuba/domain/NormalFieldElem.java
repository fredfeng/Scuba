package framework.scuba.domain;

import joeq.Class.jq_Class;
import joeq.Class.jq_Field;

public class NormalFieldElem extends FieldElem {
	// the class this field belongs to
	protected jq_Class clazz;

	// the field this represents
	protected jq_Field field;

	public NormalFieldElem(jq_Class clazz, jq_Field field) {
		this.clazz = clazz;
		this.field = field;
	}

	// getClass method
	public jq_Class getBelongingClass() {
		return this.clazz;
	}

	// getField method
	public jq_Field getField() {
		return this.field;
	}
}
