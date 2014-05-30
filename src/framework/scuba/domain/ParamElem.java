package framework.scuba.domain;

public class ParamElem extends StackObject {

	// the class this parameter belongs to

	// the method this parameter belongs to

	// the parameter of this parameter

	public ParamElem() {
		// assign the class, method and parameter to this.*
	}

	@Override
	public AbstractMemLoc findRoot() {
		return this;
	}

	// getClass method

	// getMethod method

	// getParameter method

}
