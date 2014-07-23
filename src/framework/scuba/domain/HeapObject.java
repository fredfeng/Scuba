package framework.scuba.domain;

import joeq.Class.jq_Type;

public abstract class HeapObject extends AbsMemLoc {

	public HeapObject(jq_Type type, int number) {
		super(type, number);
	}

}
