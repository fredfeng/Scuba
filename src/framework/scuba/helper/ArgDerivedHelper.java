package framework.scuba.helper;

import framework.scuba.domain.AbstractMemLoc;
import framework.scuba.domain.AbstractMemLoc.ArgDerivedType;
import framework.scuba.domain.AllocElem;
import framework.scuba.domain.LocalVarElem;
import framework.scuba.domain.ParamElem;
import framework.scuba.domain.StaticElem;

public class ArgDerivedHelper {

	public static ArgDerivedType markArgDerived(AbstractMemLoc loc) {
		// if root has been analyzed for arg-derived
		if (loc.knownArgDerived()) {
			return loc.getArgDerivedMarker();
		}

		AbstractMemLoc root = loc.findRoot();

		// if not, do the arg-derived analysis
		if (root instanceof LocalVarElem || root instanceof AllocElem) {
			loc.resetArgDerived();
		} else if (root instanceof ParamElem || root instanceof StaticElem) {
			loc.setArgDerived();
		} else {
			assert false : "ArgDerivedHelper wrong!";
		}

		return loc.getArgDerivedMarker();

	}
}
