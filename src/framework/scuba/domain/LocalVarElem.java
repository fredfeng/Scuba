package framework.scuba.domain;

public class LocalVarElem extends StackObject {

	// the class this local variable belongs to

	// the method this local variable belongs to

	// the variable of this local variable

	public LocalVarElem() {
		// assign the class, method and variable to this.*
	}

	@Override
	public AbstractMemLoc findRoot() {
		return this;
	}

	// getClass method

	// getMethod method

	// getVariable method

}
