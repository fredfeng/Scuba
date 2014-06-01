package framework.scuba.domain;

import java.util.HashMap;
import java.util.Map;

public class InstantiatedMemLocSet extends AbstractMemLocConstraintSet {
	Map<AbstractMemLoc, Constraint> instantiatedTarget;

	public InstantiatedMemLocSet() {
		instantiatedTarget = new HashMap<AbstractMemLoc, Constraint>();
	}
}
