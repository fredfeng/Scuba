package framework.scuba.domain;

import java.util.HashSet;
import java.util.Set;

import joeq.Class.jq_Type;

public abstract class AbsMemLoc {

	protected Set<FieldElem> fields = new HashSet<FieldElem>();

	protected int length;

	protected jq_Type type;
	
	public boolean isInCycle() {
		return inCycle;
	}

	public void setInCycle(boolean inCycle) {
		this.inCycle = inCycle;
	}

	protected boolean inCycle;

	public static enum ArgDerivedType {
		IS_ARG_DERIVED, NOT_ARG_DERIVED, UN_KNOWN;
	}

	ArgDerivedType argDerived = ArgDerivedType.UN_KNOWN;

	// abstract methods
	abstract public AbsMemLoc findRoot();

	public jq_Type getType() {
		return type;
	}

	public void setType(jq_Type type) {
		this.type = type;
	}

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

	abstract public boolean hasFieldType(jq_Type type);

	abstract public boolean hasFieldTypeComp(jq_Type type);

	public void addField(FieldElem field) {
		fields.add(field);
	}

	public Set<FieldElem> getFields() {
		return this.fields;
	}

	public int contxtLength() {
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
