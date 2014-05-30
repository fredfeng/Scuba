package framework.scuba.domain;

import java.util.HashMap;
import java.util.Map;

public class AbstractHeap {

	Map<AbstractMemLoc, P2Set> abstractHeap = new HashMap<AbstractMemLoc, P2Set>();

	ArgDerivedHelper argDerivedHelper = new ArgDerivedHelper();

	public AbstractHeap() {

	}

	// field look-up for location
//	public P2Set lookup(AbstractMemLoc pi, FieldElem f) {
//		if (argDerivedHelper.isArgDerived(pi)) {
//			
//		}
//	}
}
