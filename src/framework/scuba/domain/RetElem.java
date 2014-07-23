package framework.scuba.domain;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;

// TODO
// RetElem is either the parameter or the local variable in the IR
public class RetElem extends StackObject {

	protected jq_Method meth;

	public RetElem(jq_Method meth, int number) {
		super(meth.getReturnType(), number);
		this.meth = meth;
	}

	public jq_Method getMethod() {
		return meth;
	}

	public jq_Class getDeclaringClass() {
		return meth.getDeclaringClass();
	}

	// -------------- Regular ----------------
	@Override
	public String toString() {
		return "[Ret]";
	}

}
