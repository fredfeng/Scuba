package framework.scuba.helper;

import framework.scuba.domain.AbstractMemLoc;
import framework.scuba.domain.AbstractMemLoc.ArgDerivedType;
import framework.scuba.domain.AllocElem;
import framework.scuba.domain.LocalVarElem;
import framework.scuba.domain.ParamElem;

public class ArgDerivedHelper {

	public static ArgDerivedType markArgDerived(AbstractMemLoc loc) {

		AbstractMemLoc root = loc.findRoot();
		// if root has been analyzed for arg-derived
		if (root.knownArgDerived()) {
			return root.getArgDerivedMarker();
		}

		// if not, do the arg-derived analysis
		if (root instanceof LocalVarElem || root instanceof AllocElem) {
			loc.resetArgDerived();
		} else if (root instanceof ParamElem) {
			loc.setArgDerived();
		} else {
			assert false : "ArgDerivedHelper wrong!";
		}

		return root.getArgDerivedMarker();

	}
}
