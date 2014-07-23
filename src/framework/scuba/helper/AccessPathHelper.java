package framework.scuba.helper;

import java.util.LinkedList;
import java.util.Set;

import joeq.Class.jq_Array;
import joeq.Class.jq_Type;
import chord.program.Program;
import framework.scuba.domain.AbsHeap;
import framework.scuba.domain.AbsMemLoc;
import framework.scuba.domain.AccessPathElem;
import framework.scuba.domain.AllocElem;
import framework.scuba.domain.EpsilonFieldElem;
import framework.scuba.domain.FieldElem;
import framework.scuba.domain.HeapObject;
import framework.scuba.domain.IndexFieldElem;
import framework.scuba.domain.P2Set;
import framework.scuba.domain.RegFieldElem;
import framework.scuba.domain.StackObject;
import framework.scuba.domain.StaticAccessPathElem;
import framework.scuba.domain.StaticFieldElem;

public class AccessPathHelper {

	public static jq_Type rslvAPType(AbsMemLoc inner, FieldElem outer) {
		jq_Type ret = null;
		if (outer instanceof EpsilonFieldElem) {
			ret = inner.getType();
		} else if (outer instanceof IndexFieldElem) {
			if (inner.getType() instanceof jq_Array) {
				ret = ((jq_Array) inner.getType()).getElementType();
			} else {
				ret = Program.g().getType("java.lang.Object");
			}
		} else if (outer instanceof RegFieldElem) {
			ret = ((RegFieldElem) outer).getType();
		} else {
			ret = null;
			assert false : "wrong type.";
		}
		return ret;
	}

	public static LinkedList<FieldElem> fieldSeq(AccessPathElem ap) {
		LinkedList<FieldElem> ret = new LinkedList<FieldElem>();
		while (true) {
			AbsMemLoc base = ap.getBase();
			ret.addFirst(ap.getOuter());
			if (!(base instanceof AccessPathElem)) {
				break;
			}
			ap = (AccessPathElem) base;
		}
		return ret;
	}

	// given a static access path, find all the AllocElem's that this path can
	// refer to in the given heap by filling the set
	public static void resolve(AbsHeap absHeap, StaticAccessPathElem ap,
			Set<AllocElem> ret) {
		StackObject root = ap.getBase();
		assert (root instanceof StaticFieldElem) : "root should be a static element!";
		LinkedList<FieldElem> fieldSeq = AccessPathHelper.fieldSeq(ap);
		resolve(absHeap, root, fieldSeq, 0, ret);
	}

	private static void resolve(AbsHeap absHeap, AbsMemLoc loc,
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
