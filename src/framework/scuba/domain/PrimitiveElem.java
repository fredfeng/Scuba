package framework.scuba.domain;

public class PrimitiveElem extends StackObject {

	private static PrimitiveElem primitive = new PrimitiveElem();

	@Override
	public AbstractMemLoc findRoot() {
		assert false : "we cannot do find root for primitive elements!";
		return null;
	}

	@Override
	public boolean hasFieldSelector(FieldElem field) {
		assert false : "we cannot do this!";
		return false;
	}

	@Override
	public boolean equals(Object other) {
		return (other instanceof PrimitiveElem);
	}

	@Override
	public int hashCode() {
		return 32;
	}

	@Override
	public String toString() {
		return "[Primitive]";
	}

	@Override
	public String dump() {
		return "[Primitive]";
	}

	public static PrimitiveElem getPrimitiveElem() {
		return primitive;
	}

}
