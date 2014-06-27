package framework.scuba.helper;

import java.util.LinkedList;
import java.util.Set;

import framework.scuba.domain.AbsMemLoc;
import framework.scuba.domain.AbstractHeap;
import framework.scuba.domain.AccessPath;
import framework.scuba.domain.AllocElem;
import framework.scuba.domain.FieldElem;
import framework.scuba.domain.HeapObject;
import framework.scuba.domain.P2Set;
import framework.scuba.domain.StackObject;
import framework.scuba.domain.StaticAccessPath;
import framework.scuba.domain.StaticElem;

public class AccessPathHelper {

	public static LinkedList<FieldElem> fieldSeq(AccessPath ap) {
		LinkedList<FieldElem> ret = new LinkedList<FieldElem>();
		while (true) {
			AbsMemLoc base = ap.getBase();
			ret.addFirst(ap.getField());
			if (!(base instanceof AccessPath)) {
				break;
			}
			ap = (AccessPath) base;
		}
		return ret;
	}

	// given a static access path, find all the AllocElem's that this path can
	// refer to in the given heap by filling the set
	public static void resolve(AbstractHeap absHeap, StaticAccessPath ap,
			Set<AllocElem> ret) {
		StackObject root = ap.findRoot();
		assert (root instanceof StaticElem) : "root should be a static element!";
		LinkedList<FieldElem> fieldSeq = AccessPathHelper.fieldSeq(ap);
		resolve(absHeap, root, fieldSeq, 0, ret);
	}

	private static void resolve(AbstractHeap absHeap, AbsMemLoc loc,
			LinkedList<FieldElem> fieldSeq, int index, Set<AllocElem> ret) {
		P2Set p2set = absHeap.lookup(loc, fieldSeq.get(index));

		if (index == fieldSeq.size() - 1) {
			for (HeapObject hObj : p2set.keySet()) {
				if (hObj instanceof AllocElem) {
					ret.add((AllocElem) hObj);
				}
			}
		} else {
			for (HeapObject hObj : p2set.keySet()) {
				resolve(absHeap, hObj, fieldSeq, index + 1, ret);
			}
		}
	}
}
