package framework.scuba.domain;

public class EpsilonFieldElem extends FieldElem {

	private static EpsilonFieldElem e = new EpsilonFieldElem();

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
		return "[F]e";
	}

	public static EpsilonFieldElem getEpsilonFieldElem() {
		return e;
	}
}
