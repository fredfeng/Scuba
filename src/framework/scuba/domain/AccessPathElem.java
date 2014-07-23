package framework.scuba.domain;

import java.util.HashSet;
import java.util.Set;

import joeq.Class.jq_Type;
import chord.program.Program;

public abstract class AccessPathElem extends HeapObject implements AccessPath {

	protected Set<FieldElem> smashed = new HashSet<FieldElem>();

	// we should use this when referring to ending fields
	protected Set<FieldElem> endingFields = new HashSet<FieldElem>();

	final protected AbsMemLoc inner;

	final protected FieldElem outer;

	public AccessPathElem(AbsMemLoc inner, FieldElem outer, jq_Type type,
			int number) {
		super(type, number);
		this.inner = inner;
		this.outer = outer;
		addEndingField(outer);
	}

	public AbsMemLoc getInner() {
		return inner;
	}

	public FieldElem getOuter() {
		return outer;
	}

	public Set<FieldElem> getEndingFields() {
		return endingFields;
	}

	public void addEndingField(FieldElem f) {
		endingFields.add(f);
	}

	public void addEndingFields(Set<FieldElem> fields) {
		endingFields.addAll(fields);
	}

	public Set<FieldElem> getSmashedFields() {
		return smashed;
	}

	public void addSmashedField(FieldElem f) {
		assert (f instanceof RegFieldElem || f instanceof IndexFieldElem) : ""
				+ "only normal field and index field can be smashed!";
		smashed.add(f);
	}

	public void addSmashedFields(Set<FieldElem> fields) {
		smashed.addAll(fields);
	}

	public boolean isSmashed() {
		return !smashed.isEmpty();
	}

	public Set<jq_Type> getEndingFieldsTypes() {
		Set<jq_Type> ret = new HashSet<jq_Type>();
		for (FieldElem f : endingFields) {
			if (f instanceof RegFieldElem) {
				ret.add(((RegFieldElem) f).getField().getType());
			} else if (f instanceof EpsilonFieldElem) {
				ret.add(inner.getType());
			} else if (f instanceof IndexFieldElem) {
				ret.add(Program.g().getClass("java.lang.Object"));
			}
		}
		return ret;
	}

	public Set<FieldElem> getPreSmashedFields(FieldElem f) {
		Set<FieldElem> ret = new HashSet<FieldElem>();
		addFieldAsSmashed(f, ret);
		return ret;
	}

	private void addFieldAsSmashed(FieldElem f, Set<FieldElem> set) {
		if (outer.equals(f)) {
			set.addAll(smashed);
			set.add(f);
			return;
		}
		set.addAll(smashed);
		set.add(outer);
		assert (inner instanceof AccessPathElem);
		((AccessPathElem) inner).addFieldAsSmashed(f, set);
		return;
	}

	// -------------- Regular ----------------
	@Override
	public int hashCode() {
		assert (number > 0) : "AccessPathElem should have non-negative number.";
		return number;
	}

	@Override
	public boolean equals(Object other) {
		return this == other;
	}

	// --------------- AccessPath ---------------
	@Override
	public StackObject getBase() {
		if (inner instanceof StackObject) {
			return (StackObject) inner;
		}
		assert (inner instanceof AccessPath);
		return ((AccessPathElem) inner).getBase();
	}

	@Override
	public AccessPathElem getPrefix(FieldElem f) {
		if (outer.equals(f)) {
			return this;
		} else if (inner instanceof AccessPathElem) {
			return ((AccessPathElem) inner).getPrefix(f);
		}
		return null;
	}

	@Override
	public int countFieldSelector(FieldElem field) {
		if (outer.equals(field)) {
			if (inner instanceof AccessPathElem) {
				return 1 + ((AccessPathElem) inner).countFieldSelector(field);
			} else {
				return 1;
			}
		} else {
			if (inner instanceof AccessPathElem) {
				return ((AccessPathElem) inner).countFieldSelector(field);
			} else {
				return 0;
			}
		}
	}

}