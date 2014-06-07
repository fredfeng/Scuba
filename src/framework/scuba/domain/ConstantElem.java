package framework.scuba.domain;

public class ConstantElem extends StackObject {

	private static ConstantElem constant = new ConstantElem();

	@Override
	public AbstractMemLoc findRoot() {
		assert false : "we cannot do find root for constant element!";
		return null;
	}

	@Override
	public boolean hasFieldSelector(FieldElem field) {
		assert false : "we cannot do this!";
		return false;
	}

	@Override
	public boolean equals(Object other) {
		return (other instanceof ConstantElem);
	}

	@Override
	public int hashCode() {
		return 32;
	}

	@Override
	public String toString() {
		return "[Const]";
	}

	@Override
	public String dump() {
		return "[Const]";
	}

	public static ConstantElem getConstantElem() {
		return constant;
	}

}
