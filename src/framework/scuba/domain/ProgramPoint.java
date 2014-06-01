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
	public boolean equals(Object other) {
		return (other instanceof ProgramPoint)
				&& (clazz.equals(((ProgramPoint) other).getBelongingClass()))
				&& (method.equals(((ProgramPoint) other).getBelongingMethod()))
				&& (line == ((ProgramPoint) other).getLineNumber());
	}

	@Override
	public int hashCode() {
		return 37 * 37 * clazz.hashCode() + 37 * method.hashCode() + line;
	}

	@Override
	public String toString() {
		return "[C]" + clazz + " [M]" + method + "[L]" + line;
	}

}
