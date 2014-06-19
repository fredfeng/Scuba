package framework.scuba.helper;

import framework.scuba.domain.AbsMemLoc;
import framework.scuba.domain.AbsMemLoc.ArgDerivedType;
import framework.scuba.domain.AccessPath;
import framework.scuba.domain.AllocElem;
import framework.scuba.domain.LocalVarElem;
import framework.scuba.domain.ParamElem;
import framework.scuba.domain.RetElem;
import framework.scuba.domain.StaticElem;

public class ArgDerivedHelper {

	public static ArgDerivedType markArgDerived(AbsMemLoc loc) {

		if (loc.knownArgDerived()) {
			return loc.getArgDerivedMarker();
		}

		if (loc instanceof AccessPath) {
			loc.setArgDerived();
		} else if (loc instanceof AllocElem) {
			// TODO
			// we have bugs here, we cannot simply independently mark AllocElem
			// as non-arg-derived because it depends on who can point to it
			// solution: dynamically checking this and them mark
			loc.resetArgDerived();
		} else if (loc instanceof ParamElem) {
			loc.setArgDerived();
		} else if (loc instanceof StaticElem) {
			loc.setArgDerived();
		} else if (loc instanceof LocalVarElem) {
			loc.resetArgDerived();
		} else if (loc instanceof RetElem) {
			loc.resetArgDerived();
		} else {
			assert false : "wried things! Unknown type";
		}

		return loc.getArgDerivedMarker();
	}
}
