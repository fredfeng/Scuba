package framework.scuba.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import framework.scuba.helper.ConstraintManager;

public class P2Set {

	Map<HeapObject, Constraint> p2Set = new HashMap<HeapObject, Constraint>();

	public P2Set() {

	}

	public P2Set(HeapObject obj, Constraint constraint) {
		p2Set.put(obj, constraint);
	}

	// this is the default p2set
	public P2Set(HeapObject obj) {
		assert (obj.isArgDerived()) : obj + " is not argument derived!";
		assert (obj instanceof HeapObject) : obj + " is not a heap object!";
		p2Set = new HashMap<HeapObject, Constraint>();
		p2Set.put(obj, ConstraintManager.genTrue());
	}

	public boolean isEmpty() {
		return p2Set.isEmpty();
	}

	// this join method implements the join operation described in definition 8
	// of the paper, in which it only reads other and write this.p2Set
	public P2Set join(P2Set other) {
		for (HeapObject obj : other.getHeapObjects()) {
			if (p2Set.containsKey(obj)) {
				// obj is in both p2sets
				Constraint otherConstraint = other.getConstraint(obj);
				Constraint newCst = ConstraintManager.union(p2Set.get(obj),
						otherConstraint);
				p2Set.put(obj, newCst);
			} else {
				// obj is only in other's p2set
				p2Set.put(obj, other.getConstraint(obj));
			}
		}
		return this;
	}

	// this project method implements the projection operation described in
	// definition 9 of the paper, in which it writes this.p2Set and only reads
	// the other constraint
	public P2Set project(Constraint otherConstraint) {
		for (HeapObject obj : p2Set.keySet()) {
			Constraint newCst = ConstraintManager.intersect(p2Set.get(obj),
					otherConstraint);
			p2Set.put(obj, newCst);
		}
		return this;
	}

	// replace the previous value (constraint) in the map
	// if previously not in the map, return null
	// otherwise, return the previous value mapped by key (obj)
	// interesting!
	public Constraint put(HeapObject obj, Constraint constraint) {
		return p2Set.put(obj, constraint);
	}

	public boolean containsHeapObject(HeapObject obj) {
		return p2Set.containsKey(obj);
	}

	public Set<HeapObject> getHeapObjects() {
		return p2Set.keySet();
	}

	public Constraint getConstraint(HeapObject obj) {
		// if ptSet contains obj, then return that obj otherwise, return null
		return p2Set.get(obj);
	}

	// do a shallow copy
	public P2Set clone() {
		P2Set ret = new P2Set();

		for (HeapObject obj : p2Set.keySet()) {
			ret.put(obj, p2Set.get(obj));
		}

		return ret;
	}

}
