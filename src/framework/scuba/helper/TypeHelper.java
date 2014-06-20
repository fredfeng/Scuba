package framework.scuba.helper;

import joeq.Class.jq_Field;
import joeq.Class.jq_Type;
import framework.scuba.domain.EpsilonFieldElem;
import framework.scuba.domain.FieldElem;
import framework.scuba.domain.IndexFieldElem;
import framework.scuba.domain.NormalFieldElem;

public class TypeHelper {

	public static boolean typeCompatible(FieldElem f1, FieldElem f2) {
		boolean ret = false;
		if (f1 instanceof EpsilonFieldElem && f2 instanceof EpsilonFieldElem) {
			ret = true;
		} else if (f1 instanceof EpsilonFieldElem
				|| f2 instanceof EpsilonFieldElem) {
			ret = true;
		} else if (f1 instanceof IndexFieldElem && f2 instanceof IndexFieldElem) {
			ret = true;
		} else if (f1 instanceof IndexFieldElem || f2 instanceof IndexFieldElem) {
			ret = false;
		} else if (f1 instanceof NormalFieldElem
				&& f2 instanceof NormalFieldElem) {
			jq_Field jf1 = ((NormalFieldElem) f1).getField();
			jq_Field jf2 = ((NormalFieldElem) f2).getField();
			jq_Type type1 = jf1.getType();
			jq_Type type2 = jf2.getType();
			if (type1.isSubtypeOf(type2) || type2.isSubtypeOf(type1)) {
				ret = true;
			}
		} else {
			assert false : "wrong!";
		}

		return ret;
	}
}
