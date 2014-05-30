package framework.scuba.utils;

import framework.scuba.domain.Constraint;
import framework.scuba.domain.HeapObject;
import framework.scuba.domain.P2Set;

public class P2SetUtils {

	public P2Set project(P2Set p2Set, Constraint otherConstraint) {
		P2Set ret = new P2Set();
		for (HeapObject obj : p2Set.getP2HeapObjects()) {
			Constraint newConstraint = ConstraintUtils.intersect(
					p2Set.getConstraint(obj), otherConstraint);
			ret.put(obj, newConstraint);
		}

		return ret;
	}

	public static P2Set join(P2Set pt1, P2Set pt2) {

		P2Set ret = pt1.clone();
		for (HeapObject obj : pt2.getP2HeapObjects()) {
			if (ret.containsHeapObject(obj)) {
				Constraint newConstraint = ConstraintUtils.intersect(
						ret.getConstraint(obj), pt2.getConstraint(obj));
				ret.put(obj, newConstraint);
			} else {
				ret.put(obj, pt2.getConstraint(obj));
			}
		}

		return ret;

	}
}
