package framework.scuba.domain;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.RegisterFactory.Register;

public class LocalVarElem extends StackObject {

	protected Register local;

	protected jq_Method meth;

	protected jq_Class clazz;

	public LocalVarElem(Register local, jq_Method meth, jq_Class clazz,
			jq_Type type, int number) {
		super(type, number);
		this.local = local;
		this.meth = meth;
		this.clazz = clazz;
	}

	public Register getRegister() {
		return local;
	}

	public jq_Method getMethod() {
		return meth;
	}

	public jq_Class getDeclaringClass() {
		return clazz;
	}

	// ------------- Regular --------------
	@Override
	public String toString() {
		return "[Local]" + local;
	}

}
