package framework.scuba.domain;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.RegisterFactory.Register;

public class ParamElem extends StackObject {

	protected Register parameter;

	protected jq_Method meth;

	protected jq_Class clazz;

	public ParamElem(Register parameter, jq_Method meth, jq_Class clazz,
			jq_Type type, int number) {
		super(type, number);
		this.parameter = parameter;
		this.meth = meth;
		this.clazz = clazz;
	}

	public Register getRegister() {
		return parameter;
	}

	public jq_Method getMethod() {
		return meth;
	}

	public jq_Class getDeclaringClass() {
		return clazz;
	}

	// -------------- Regular ---------------
	@Override
	public String toString() {
		return "[P]" + parameter;
	}

}
