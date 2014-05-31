package framework.scuba.domain;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.RegisterFactory.Register;

public class LocalVarElem extends StackObject {

	// the class this local variable belongs to
	protected jq_Class clazz;

	// the method this local variable belongs to
	protected jq_Method method;

	// the variable of this local variable
	protected Register local;

	public LocalVarElem(jq_Class clazz, jq_Method method, Register local) {
		this.clazz = clazz;
		this.method = method;
		this.local = local;
	}

	@Override
	public AbstractMemLoc findRoot() {
		return this;
	}

	@Override
	public boolean equals(Object other) {
		return (other instanceof LocalVarElem)
				&& (clazz.equals(((LocalVarElem) other).clazz))
				&& (method.equals(((LocalVarElem) other).method))
				&& (local.equals(((LocalVarElem) other).local));
	}

	@Override
	public int hashCode() {
		return 37 * 37 * clazz.hashCode() + 37 * method.hashCode()
				+ local.hashCode();
	}

	@Override
	public String toString() {

		return "[Class] " + clazz + " [Method] " + method + " [LocalVar] "
				+ local;
	}

	// getClass method
	public jq_Class getBelongingClass() {
		return this.clazz;
	}

	// getMethod method
	public jq_Method getBelongingMethod() {
		return this.method;
	}

	// getVariable method
	public Register getLocal() {
		return this.local;
	}

	@Override
	public boolean hasFieldSelector(FieldElem field) {
		return false;
	}

}
