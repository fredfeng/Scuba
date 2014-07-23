package framework.scuba.domain;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;

public class ProgPoint implements Numberable {

	final protected Quad stmt;

	// number starts from 1
	private int number;

	public ProgPoint(Quad stmt, int number) {
		this.stmt = stmt;
		setNumber(number);
	}

	public jq_Method getMethod() {
		return stmt.getMethod();
	}

	// -------------- Numberable ----------------
	@Override
	public int getNumber() {
		return number;
	}

	@Override
	public void setNumber(int number) {
		this.number = number;
	}

	// ------------ Regular --------------
	@Override
	public int hashCode() {
		assert number > 0 : "[PP] should have non-negative number.";
		return number;
	}

	@Override
	public boolean equals(Object other) {
		return this == other;
	}

	@Override
	public String toString() {
		return "[PP: " + number + " " + stmt + "]";
	}
}
