package framework.scuba.domain;

import java.util.Map;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;

public class FormalsToActualsMemLocMapping {

	// mapping from memory locations of the formals of the callee to the memory
	// locations of the actuals of the caller
	protected Map<AbstractMemLoc, AbstractMemLoc> formalsToActuals;

	public FormalsToActualsMemLocMapping(Quad callsite, SummariesEnv env,
			jq_Method caller, jq_Method callee) {
		
	}
}
