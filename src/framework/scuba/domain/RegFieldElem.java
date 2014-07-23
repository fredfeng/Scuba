package framework.scuba.domain;

import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import joeq.Class.jq_Type;

public class RegFieldElem extends FieldElem {

	protected jq_Field field;

	public RegFieldElem(jq_Field field, int number) {
		super(number);
		this.field = field;
	}

	public jq_Field getField() {
		return field;
	}

	public jq_Class getDeclaringClass() {
		return field.getDeclaringClass();
	}

	public jq_Type getType() {
		return field.getType();
	}

	// ---------- Regular ------------
	@Override
	public String toString() {
		return "[F]" + field;
	}
}
