package framework.scuba.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import framework.scuba.helper.ConstraintManager;

public class InstantiatedLocSet {

	protected Map<AbstractMemLoc, Constraint> instnLocSet = new HashMap<AbstractMemLoc, Constraint>();

	public InstantiatedLocSet() {

	}

	public InstantiatedLocSet(AbstractMemLoc loc, Constraint constraint) {
		instnLocSet.put(loc, constraint);
	}

	public void put(AbstractMemLoc loc, Constraint constraint) {
		instnLocSet.put(loc, constraint);
	}

	public boolean containsAbstractMemLoc(AbstractMemLoc loc) {
		return instnLocSet.containsKey(loc);
	}

	public Set<AbstractMemLoc> getAbstractMemLocs() {
		return instnLocSet.keySet();
	}

	public Constraint getConstraint(AbstractMemLoc loc) {
		return instnLocSet.get(loc);
	}

	public boolean isEmpty() {
		return instnLocSet.isEmpty();
	}

	public int size() {
		return instnLocSet.size();
	}

	public boolean join(P2Set other) {
		boolean ret = false;
		for (HeapObject hObj : other.getHeapObjects()) {
			if (instnLocSet.containsKey(hObj)) {
				// loc is in both sets
				// directly get the other set's constraints
				Constraint otherCst = other.getConstraint(hObj);

				// check whether we need to union the constraint
				// TODO maybe we can comment the following
				if (instnLocSet.get(hObj).equals(otherCst))
					continue;

				// generate the union of the two (a shallow copy with the same
				// constraints but different instances)
				Constraint newCst = ConstraintManager.union(
						instnLocSet.get(hObj), otherCst);

				instnLocSet.put(hObj, newCst);
				ret = true;
			} else {
				// loc is only in other's set
				// AVOID directly get the constraint instance of the other
				// set!!!! only get the shallow copy of the other constraints

				// for this case, we should add a new edge
				instnLocSet.put(hObj, other.getConstraint(hObj).clone());
				ret = true;
			}
		}
		return ret;
	}
}
