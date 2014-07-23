package framework.scuba.domain;

import joeq.Class.jq_Class;
import joeq.Class.jq_Field;

public class StaticFieldElem extends StackObject {

	protected jq_Field staticField;

	public StaticFieldElem(jq_Field staticField, int number) {
		super(staticField.getType(), number);
		this.staticField = staticField;
	}

	public jq_Class getDeclaringClass() {
		return staticField.getDeclaringClass();
	}

	public jq_Field getStaticField() {
		return staticField;
	}

	// --------------- Regular -----------------
	@Override
	public String toString() {
		return "[SF]" + staticField;
	}

}
