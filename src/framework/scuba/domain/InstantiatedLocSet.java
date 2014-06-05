package framework.scuba.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class InstantiatedLocSet {

	protected Map<AbstractMemLoc, Constraint> instnLocSet = new HashMap<AbstractMemLoc, Constraint>();

	public InstantiatedLocSet(AbstractMemLoc loc, Constraint constraint) {
		instnLocSet.put(loc, constraint);
	}

	public void put(AbstractMemLoc loc, Constraint constraint) {
		instnLocSet.put(loc, constraint);
	}

	public boolean containsAbstractMemLoc(AbstractMemLoc loc) {
		return instnLocSet.containsKey(loc);
	}

	public Set<AbstractMemLoc> getAbstractMemLocs() {
		return instnLocSet.keySet();
	}

}
