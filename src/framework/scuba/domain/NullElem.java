package framework.scuba.domain;

import java.util.Set;

public class NullElem extends HeapObject {

	@Override
	public AbstractMemLoc findRoot() {
		return this;
	}

	@Override
	public boolean hasFieldSelector(FieldElem field) {
		return false;
	}

	@Override
	public boolean equals(Object other) {
		return (other instanceof NullElem);
	}

	@Override
	public int hashCode() {
		return 1;
	}

	@Override
	public String toString() {
		return "[Null]";
	}

	@Override
	public void addField(FieldElem field) {
		assert false : "You can NOT add fields to a NullElem!!";
	}

	@Override
	public Set<FieldElem> getFields() {
		return this.fields;
	}

}
