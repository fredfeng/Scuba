package framework.scuba.domain;

import java.util.HashMap;
import java.util.Map;

// when doing the instantiation
// we have the heap for the caller, H(caller) = [(a,b)-->(c,d)]
// and the heap for the callee, H(callee) = [(a',b')-->(c',d')]
// foreach 
public class InstantiatedMemLocSet {

	Map<AbstractMemLoc, Constraint> instanMemLocSet = new HashMap<AbstractMemLoc, Constraint>();

	public InstantiatedMemLocSet() {

	}

}
