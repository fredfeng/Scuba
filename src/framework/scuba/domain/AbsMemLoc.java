package framework.scuba.domain;

import java.util.HashSet;
import java.util.Set;

import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BytecodeToQuad.jq_ReturnAddressType;

public abstract class AbsMemLoc implements Numberable, ArgDerivable {

	protected int number;

	protected final jq_Type type;

	protected final Set<FieldElem> fields = new HashSet<FieldElem>();

	ArgDerivable.ArgDvdType argDvdMarker = ArgDerivable.ArgDvdType.UN_KNOWN;

	public AbsMemLoc(jq_Type type, int number) {
		if (!type.isPrepared() && !(type instanceof jq_ReturnAddressType)) {
			type.prepare();
		}
		this.type = type;
		setNumber(number);
	}

	public jq_Type getType() {
		return type;
	}

	public void addField(FieldElem field) {
		fields.add(field);
	}

	public Set<FieldElem> getFields() {
		return fields;
	}

	// -------------- Regular ------------------
	@Override
	public int hashCode() {
		assert number > 0 : "AbsMemLoc should have non-negative number.";
		return number;
	}

	@Override
	public boolean equals(Object other) {
		return this == other;
	}

	// ---------------- Numberable ------------------
	@Override
	public int getNumber() {
		return number;
	}

	@Override
	public void setNumber(int number) {
		this.number = number;
	}

	// ---------------- ArgDerivable ------------------
	@Override
	public void setArgDvd() {
		this.argDvdMarker = ArgDerivable.ArgDvdType.IS_ARG_DERIVED;
	}

	@Override
	public void resetArgDvd() {
		this.argDvdMarker = ArgDerivable.ArgDvdType.NOT_ARG_DERIVED;
	}

	@Override
	public ArgDerivable.ArgDvdType getArgDvdMarker() {
		return this.argDvdMarker;
	}

	@Override
	public boolean knownArgDvd() {
		return (this.argDvdMarker != ArgDerivable.ArgDvdType.UN_KNOWN);
	}

	@Override
	public boolean unknowArgDvd() {
		return (this.argDvdMarker == ArgDerivable.ArgDvdType.UN_KNOWN);
	}

	@Override
	public boolean isArgDvd() {
		return (this.argDvdMarker == ArgDerivable.ArgDvdType.IS_ARG_DERIVED);
	}

	@Override
	public boolean isNotArgDvd() {
		return (this.argDvdMarker == ArgDerivable.ArgDvdType.NOT_ARG_DERIVED);
	}

}
