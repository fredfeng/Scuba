package framework.scuba.domain;

import java.util.HashMap;

public class DefaultP2Set extends ConcreteP2Set {

	public DefaultP2Set(HeapObject obj) {
		assert (obj.isArgDerived()) : obj + " is not argument derived!";
		assert (obj instanceof HeapObject) : obj + " is not a heap object!";
		p2Set = new HashMap<HeapObject, Constraint>();
		p2Set.put(obj, new TrueConstraint());
	}
}
