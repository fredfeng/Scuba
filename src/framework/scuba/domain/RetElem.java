package framework.scuba.domain;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.RegisterFactory.Register;

// TODO
// RetElem is either the parameter or the local variable in the IR
public class RetElem extends StackObject {
	// the class this local variable belongs to
	protected jq_Class clazz;

	// the method this local variable belongs to
	protected jq_Method method;

	// the variable of this return value
	protected Register retValue;

	public RetElem(jq_Class clazz, jq_Method method, Register retValue) {
		this.clazz = clazz;
		this.method = method;
		this.retValue = retValue;
	}

	@Override
	public RetElem findRoot() {
		return this;
	}

	@Override
	public boolean hasFieldSelector(FieldElem field) {
		// a return value has no field selector
		return false;
	}

	@Override
	public boolean equals(Object other) {
		return (other instanceof RetElem)
				&& (clazz.equals(((RetElem) other).clazz))
				&& (method.equals(((RetElem) other).method))
				&& (retValue.equals(((RetElem) other).retValue));
	}

	@Override
	public int hashCode() {
		return 37 * 37 * clazz.hashCode() + 37 * method.hashCode()
				+ retValue.hashCode();
	}

	@Override
	public String toString() {
		return "[R]" + retValue;
	}

	@Override
	public String dump() {
		return "[C] " + clazz.getName() + " [M] " + method.getName() + " [R] "
				+ retValue;
	}

	// getClass method
	public jq_Class getBelongingClass() {
		return this.clazz;
	}

	// getMethod method
	public jq_Method getBelongingMethod() {
		return this.method;
	}

	// getRetValue method
	public Register getRetValue() {
		return this.retValue;
	}

}
