package framework.scuba.domain;

import java.util.HashSet;
import java.util.Set;

public abstract class AbsMemLoc {

	protected Set<FieldElem> fields = new HashSet<FieldElem>();

	protected int length;

	public static enum ArgDerivedType {
		IS_ARG_DERIVED, NOT_ARG_DERIVED, UN_KNOWN;
	}

	ArgDerivedType argDerived = ArgDerivedType.UN_KNOWN;

	// abstract methods
	abstract public AbsMemLoc findRoot();

	public void setArgDerived() {
		this.argDerived = ArgDerivedType.IS_ARG_DERIVED;
	}

	public void resetArgDerived() {
		this.argDerived = ArgDerivedType.NOT_ARG_DERIVED;
	}

	public ArgDerivedType getArgDerivedMarker() {
		return this.argDerived;
	}

	public boolean knownArgDerived() {
		return (this.argDerived != ArgDerivedType.UN_KNOWN);
	}

	public boolean unknowArgDerived() {
		return (this.argDerived == ArgDerivedType.UN_KNOWN);
	}

	public boolean isArgDerived() {
		return (this.argDerived == ArgDerivedType.IS_ARG_DERIVED);
	}

	public boolean isNotArgDerived() {
		return (this.argDerived == ArgDerivedType.NOT_ARG_DERIVED);
	}

	abstract public boolean hasFieldSelector(FieldElem field);

	// count how many field selectors are there in the given element
	abstract public int countFieldSelector(FieldElem field);

	public void addField(FieldElem field) {
		fields.add(field);
	}

	public Set<FieldElem> getFields() {
		return this.fields;
	}

	public int length() {
		return length;
	}

	@Override
	abstract public boolean equals(Object other);

	@Override
	abstract public int hashCode();

	@Override
	abstract public String toString();

	abstract public String dump();
}
