package framework.scuba.domain;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.RegisterFactory.Register;

public class LocalVarElem extends StackObject {

	// the class this local variable belongs to
	protected jq_Class clazz;

	// the method this local variable belongs to
	protected jq_Method method;

	// the variable of this local variable
	protected Register local;

	public LocalVarElem(jq_Class clazz, jq_Method method, Register local,
			jq_Type type) {
		this.clazz = clazz;
		this.method = method;
		this.local = local;
		this.length = 1;
		this.type = type;
	}

	public LocalVarElem(jq_Class clazz, jq_Method method, Register local) {
		this.clazz = clazz;
		this.method = method;
		this.local = local;
		this.length = 1;
	}

	@Override
	public LocalVarElem findRoot() {
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

		return "[C]" + clazz.getName() + "[M]" + method.getName()
				+ "[LocalVar]" + local;
		// return "[V]" + local;
	}

	@Override
	public String dump() {
		return "[C]" + clazz.getName() + "[M]" + method.getName()
				+ "[LocalVar]" + local;
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

	@Override
	public int countFieldSelector(FieldElem field) {
		return 0;
	}

}
