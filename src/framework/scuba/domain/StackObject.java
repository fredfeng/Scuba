package framework.scuba.domain;

import joeq.Class.jq_Type;

public abstract class StackObject extends AbsMemLoc {

	public StackObject(jq_Type type, int number) {
		super(type, number);
	}

	@Override
	public void addField(FieldElem field) {
		assert (field instanceof EpsilonFieldElem) : ""
				+ "Only EpsilonFieldElem is allowed for StackObject.";
		fields.add(field);
	}

}
