package framework.scuba.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ConcreteP2Set extends P2Set {

	public ConcreteP2Set() {
		p2Set = new HashMap<HeapObject, Constraint>();
	}

	public ConcreteP2Set(HeapObject obj, Constraint constraint) {
		p2Set = new HashMap<HeapObject, Constraint>();
		p2Set.put(obj, constraint);
	}

	@Override
	public P2Set clone() {
		P2Set ret = new ConcreteP2Set();

		for (HeapObject obj : p2Set.keySet()) {
			ret.put(obj, p2Set.get(obj));
		}

		return ret;
	}

}
