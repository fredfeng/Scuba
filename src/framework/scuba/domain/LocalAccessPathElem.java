package framework.scuba.domain;

import joeq.Class.jq_Type;

public class LocalAccessPathElem extends AccessPathElem {

	public LocalAccessPathElem(AbsMemLoc inner, FieldElem outer, jq_Type type,
			int number) {
		super(inner, outer, type, number);
	}

	// -------------- Regular --------------
	@Override
	public String toString() {
		return "[Inner]" + inner + "[Outer]" + outer;
	}

}