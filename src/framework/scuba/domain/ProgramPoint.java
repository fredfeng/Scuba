package framework.scuba.domain;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;

public class ProgramPoint {

	// the class this program point is in
	protected jq_Class clazz;

	// the method this program point is in
	protected jq_Method method;

	// the line number of this program point in the method of the class
	protected int line;

	public ProgramPoint(jq_Class clazz, jq_Method method, int line) {
		this.clazz = clazz;
		this.method = method;
		this.line = line;
	}

	// getClass() method
	protected jq_Class getBelongingClass() {
		return this.clazz;
	}

	// getMethod() method
	protected jq_Method getBelongingMethod() {
		return this.method;
	}

	// getLineNumber() method
	protected int getLineNumber() {
		return this.line;
	}

	@Override
	public String toString() {
		return "[Class] " + clazz + " [Method] " + method + " [Line] " + line;
	}

}
