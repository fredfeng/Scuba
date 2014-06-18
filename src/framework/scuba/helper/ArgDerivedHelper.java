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

		// assert (loc.unknowArgDerived());
		// if root has been analyzed for arg-derived
		if (loc.knownArgDerived()) {
			return loc.getArgDerivedMarker();
		}

		if (loc instanceof AccessPath) {
			loc.setArgDerived();
		} else if (loc instanceof AllocElem) {
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

		// AbstractMemLoc root = loc.findRoot();
		//
		// // if not, do the arg-derived analysis
		// if (root instanceof LocalVarElem || root instanceof AllocElem
		// || root instanceof RetElem) {
		// loc.resetArgDerived();
		// assert loc.isNotArgDerived();
		// } else if (root instanceof ParamElem || root instanceof StaticElem) {
		// // a.\e.f and (A.f).\e.g are both possible
		// loc.setArgDerived();
		// assert loc.isArgDerived();
		// } else {
		// assert false : "ArgDerivedHelper wrong!";
		// }
		//
		// // TODO
		// // if the following never fails, we can change the above codes to do
		// the
		// // marking so that we do not need to call the findRoot method and
		// save
		// // time
		// if (loc instanceof AccessPath) {
		// assert loc.isArgDerived() : "AccessPath are all arg-derived";
		// } else if (loc instanceof AllocElem) {
		// assert loc.isNotArgDerived() : "AllocElem is not arg-derived";
		// } else if (loc instanceof ParamElem) {
		// assert loc.isArgDerived() : "ParamElem is arg-derived";
		// } else if (loc instanceof StaticElem) {
		// assert loc.isArgDerived() : "StaticElem is arg-derived";
		// } else if (loc instanceof LocalVarElem) {
		// assert loc.isNotArgDerived() :
		// "LocalVarElem shold not be arg-derived";
		// } else if (loc instanceof RetElem) {
		// assert loc.isNotArgDerived() : "RetElem is not arg-derived";
		// } else {
		// assert false : "wried things! Unknown type";
		// }

		return loc.getArgDerivedMarker();

	}
}
