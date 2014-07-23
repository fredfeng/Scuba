package framework.scuba.helper;

import framework.scuba.domain.AbsMemLoc;
import framework.scuba.domain.AccessPathElem;
import framework.scuba.domain.AllocElem;
import framework.scuba.domain.LocalVarElem;
import framework.scuba.domain.ParamElem;
import framework.scuba.domain.RetElem;
import framework.scuba.domain.StaticFieldElem;

public class DefaultTargetHelper {

	public static boolean hasDefault(AbsMemLoc loc) {
		if (loc instanceof ParamElem || loc instanceof StaticFieldElem
				|| loc instanceof AccessPathElem) {
			return true;
		} else if (loc instanceof AllocElem || loc instanceof LocalVarElem
				|| loc instanceof RetElem) {
			return false;
		} else {
			assert false : "unknown memory location.";
			return false;
		}
	}
}
