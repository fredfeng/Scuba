package framework.scuba.domain;

import joeq.Class.jq_Type;

public class PrimitiveElem extends StackObject {

	private static PrimitiveElem primitive = new PrimitiveElem();

	public PrimitiveElem() {
		this.length = 1;
	}

	@Override
	public AbsMemLoc findRoot() {
		assert false : "we cannot do find root for primitive elements!";
		return null;
	}

	@Override
	public boolean hasFieldSelector(FieldElem field) {
		assert false : "we cannot do this!";
		return false;
	}

	@Override
	public int countFieldSelector(FieldElem field) {
		assert false : "we cannot do this!";
		return 0;
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

	@Override
	public boolean hasFieldType(jq_Type type) {
		assert false : "we cannot do this!";
		return false;
	}

	@Override
	public boolean hasFieldTypeComp(jq_Type type) {
		assert false : "we cannot do this!";
		return false;
	}

}
