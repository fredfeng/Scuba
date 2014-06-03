package framework.scuba.domain;

public class IndexFieldElem extends FieldElem {

	private static IndexFieldElem index = new IndexFieldElem();

	@Override
	public boolean equals(Object other) {
		return (other instanceof IndexFieldElem);
	}

	@Override
	public int hashCode() {
		return 22;
	}

	@Override
	public String toString() {
		return "[I]i";
	}

	public static IndexFieldElem getIndexFieldElem() {
		return index;
	}

}
