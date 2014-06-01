package framework.scuba.domain;

import java.util.HashMap;
import java.util.Map;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import chord.util.tuple.object.Pair;

public class MemLocInstantiation {

	// this maps from the abstract memory locations in the heap of the callee to
	// the abstract memory locations in the heap of the caller
	protected Map<AbstractMemLoc, InstantiatedMemLocSet> instantiatedMemLocMapping;

	// this maps from the P2Set in the heap of the callee to the P2Set
	protected Map<P2Set, P2Set> instantiatedP2SetMapping;

	// this maps from the Chord variables in the bytecode in the callee to the
	// Chord variables in the bytecode in the caller
	// this map is used as the base case for memory location instantiation
	// it should include the following:
	// 1. formals in the param list of the callee --> actuals of the callsite in
	// the caller [real parameters mapping, should be a Register-->Register map]
	// 2. the returned value of the callee --> the LHS operand in the callsite
	// if there exists a LHS operand, e.g. x = foo(a,b) where x is the LHS
	// operand [return value mapping, should be a Register-->Register map]
	// 3. the static class (related to static field reference, e.g. A.f) in the
	// callee --> the static class itself [static class mapping, should be a
	// jq_Class --> jq_Class map]
	protected Map<Object, Object> formalsToActuals;

	public MemLocInstantiation(Quad callsite, SummariesEnv env,
			jq_Method caller, jq_Method callee) {
		instantiatedMemLocMapping = new HashMap<AbstractMemLoc, InstantiatedMemLocSet>();
		instantiatedP2SetMapping = new HashMap<P2Set, P2Set>();
		formalsToActuals = new HashMap<Object, Object>();

		// first fill the formals to actuals map so that we have the base case
		fillFormalsToActualsMap(callsite, caller, callee);
		// then fill the memory locations map

	}

	public void instantiate() {

	}

	public Map<Object, Object> fillFormalsToActualsMap(Quad callsite,
			jq_Method caller, jq_Method callee) {
		// fill the formalsToActuals map by the following:
		// 1. according to the information contained in the callsite of the
		// caller and the parameter list of the callee, fill the map
		// 2. according to the return value of the callee, and the LHS operand
		// in the callsite fill the map
		// 3. find all the static field reference like A.f in the callee's
		// bytecode, and then map to itself
		return formalsToActuals;
	}
}
