package framework.scuba.domain;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.RegisterFactory.Register;

public class ParamElem extends StackObject {

	// the class this parameter belongs to
	jq_Class clazz;

	// the method this parameter belongs to
	jq_Method method;

	// the parameter of this parameter
	Register parameter;

	public ParamElem(jq_Class clazz, jq_Method method, Register parameter) {
		this.clazz = clazz;
		this.method = method;
		this.parameter = parameter;
	}

	@Override
	public AbstractMemLoc findRoot() {
		return this;
	}

	@Override
	public boolean equals(Object other) {
		return (other instanceof ParamElem)
				&& (clazz.equals(((ParamElem) other).clazz))
				&& (method.equals(((ParamElem) other).method))
				&& (parameter.equals(((ParamElem) other).parameter));
	}

	@Override
	public int hashCode() {
		return 37 * 37 * clazz.hashCode() + 37 * method.hashCode()
				+ parameter.hashCode();
	}

	@Override
	public String toString() {
		return "[Class] " + clazz + " [Method] " + method + " [Parameter] "
				+ parameter;
	}

	// getClass method
	public jq_Class getBelongingClass() {
		return this.clazz;
	}

	// getMethod method
	public jq_Method getBelongingMethod() {
		return this.method;
	}

	// getParameter method
	public Register getParameter() {
		return this.parameter;
	}

}
