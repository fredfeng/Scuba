package framework.scuba.domain;

public abstract class FieldElem implements Numberable {

	// no type information for FieldElem
	// but RegFieldElem has type information
	protected int number;

	public FieldElem(int number) {
		setNumber(number);
	}

	// -------------- Numberable -------------
	@Override
	public void setNumber(int number) {
		this.number = number;
	}

	@Override
	public int getNumber() {
		return number;
	}

	// --------------- Regular --------------
	@Override
	public int hashCode() {
		assert (number > 0) : "FieldElem should have non-negative number.";
		return number;
	}

	@Override
	public boolean equals(Object other) {
		return this == other;
	}

}