package framework.scuba.domain;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;

// TODO
// RetElem is either the parameter or the local variable in the IR
public class RetElem extends StackObject {
	// the class this local variable belongs to
	protected jq_Class clazz;

	// the method this local variable belongs to
	protected jq_Method method;

	// the variable of this return value
	// we might have multiple returns so that we cannot bind one register to the
	// return value
	// protected Register retValue;

	public RetElem(jq_Class clazz, jq_Method method) {
		this.clazz = clazz;
		this.method = method;
		// this.retValue = retValue;
		this.length = 1;
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
				&& (method.equals(((RetElem) other).method));
	}

	@Override
	public int hashCode() {
		return 37 * clazz.hashCode() + method.hashCode();
	}

	@Override
	public String toString() {
		return "[Ret]";
	}

	@Override
	public String dump() {
		return "[Return]" + "[C] " + clazz.getName() + "[M] "
				+ method.getName();
	}

	// getClass method
	public jq_Class getBelongingClass() {
		return this.clazz;
	}

	// getMethod method
	public jq_Method getBelongingMethod() {
		return this.method;
	}

}
