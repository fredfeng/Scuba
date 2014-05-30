package framework.scuba.domain;

public class ArgDerivedHelper {

	public boolean isArgDerived(AbstractMemLoc pi) {
		AbstractMemLoc root = pi.findRoot();
		// if root has been analyzed for arg-derived
		if (root.knownArgDerived()) {
			return root.isArgDerived();
		}
		// if not, do the arg-derived analysis
		if (root instanceof LocalVarElem || root instanceof AllocElem) {
			pi.resetArgDerived();
			return false;
		} else if (root instanceof ParamElem) {
			pi.setArgDerived();
			return true;
		} else {
			assert false : "ArgDerivedHelper wrong!";
			return false;
		}
	}
}