package framework.scuba.domain;

public class EpsilonFieldElem extends FieldElem {

	@Override
	public boolean equals(Object other) {
		return (other instanceof EpsilonFieldElem);
	}

	@Override
	public int hashCode() {
		return 11;
	}

	@Override
	public String toString() {
		return "[Field] Epsilon Field";
	}

}
