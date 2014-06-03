package framework.scuba.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import framework.scuba.helper.ConstraintManager;

public class P2Set {

	Map<HeapObject, Constraint> p2Set = new HashMap<HeapObject, Constraint>();

	// this is an empty p2set which is often used in dealing with null
	public P2Set() {

	}

	public P2Set(HeapObject obj, Constraint constraint) {
		p2Set.put(obj, constraint);
	}

	// this is the default p2set
	public P2Set(HeapObject obj) {
		assert (obj.isArgDerived()) : obj + " is not argument derived!";
		assert (obj instanceof HeapObject) : obj + " is not a heap object!";
		p2Set.put(obj, ConstraintManager.genTrue());
	}

	public boolean isEmpty() {
		return p2Set.isEmpty();
	}

	public Constraint remove(HeapObject hObj) {
		return p2Set.remove(hObj);
	}

	// this join method implements the join operation described in definition 8
	// of the paper, in which it only reads other and write this.p2Set
	// this method will never get the pointer to the other p2set so do not worry
	// about modifying the other p2set by modifying this p2set
	public boolean join(P2Set other) {
		boolean ret = false;
		for (HeapObject obj : other.getHeapObjects()) {
			if (p2Set.containsKey(obj)) {
				// obj is in both p2sets
				// directly get the other p2set's constraints
				Constraint otherCst = other.getConstraint(obj);

				// check whether we need to update the p2set of this heap object
				if (p2Set.get(obj).equals(otherCst))
					continue;

				// generate the union of the two (a shallow copy with the same
				// constraints but different instances)
				Constraint newCst = ConstraintManager.union(p2Set.get(obj),
						otherCst);

				p2Set.put(obj, newCst);
				ret = true;
			} else {
				// obj is only in other's p2set
				// AVOID directly get the constraint instance of the other
				// p2set!!!! only get the shallow copy of the other constraints

				// for this case, we should add a new edge
				p2Set.put(obj, other.getConstraint(obj).clone());
				ret = true;
			}
		}
		return ret;
	}

	// this project method implements the projection operation described in
	// definition 9 of the paper, in which it writes this.p2Set and only reads
	// the other constraint
	// this method will never modify the other constraints, either not get the
	// pointer to the other constraint
	public P2Set project(Constraint otherCst) {
		for (HeapObject obj : p2Set.keySet()) {
			// this newCst is a copy with the same content but different
			// constraint instances
			Constraint newCst = ConstraintManager.intersect(p2Set.get(obj),
					otherCst);
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

	// return null or return true constraint?
	public Constraint getConstraint(HeapObject obj) {
		// if ptSet contains obj then return that obj, otherwise return null
		return p2Set.get(obj);
	}

	// do a shallow copy (only shallowly copying the constraints)
	public P2Set clone() {
		P2Set ret = new P2Set();

		for (HeapObject obj : p2Set.keySet()) {
			ret.put(obj, p2Set.get(obj));
		}

		return ret;
	}

	public int size() {
		return p2Set.size();
	}

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof P2Set))
			return false;

		P2Set otherPT = (P2Set) other;

		if (p2Set.size() != otherPT.size())
			return false;

		for (HeapObject hObj : p2Set.keySet()) {
			if (otherPT.containsHeapObject(hObj)) {
				Constraint otherCst = otherPT.getConstraint(hObj);
				Constraint thisCst = p2Set.get(hObj);
				if (!thisCst.equals(otherCst))
					return false;
			} else {
				return false;
			}
		}

		return true;
	}

	@Override
	public int hashCode() {
		if (p2Set.isEmpty())
			return 0;

		int ret = 0;
		int i = 0;
		int range = 3;
		for (HeapObject hObj : p2Set.keySet()) {
			ret *= 37;
			ret += hObj.hashCode();
			ret *= 37;
			ret += p2Set.get(hObj).hashCode();
			i++;
			if (i > range)
				break;
		}

		return ret;
	}

	@Override
	public String toString() {
		StringBuilder ret = new StringBuilder("{");
		for (HeapObject hObj : p2Set.keySet()) {
			ret.append("(").append(hObj).append(",");
			ret.append(p2Set.get(hObj)).append(") ");
		}
		ret.append("}");
		return ret.toString();
	}
}
