package framework.scuba.domain;

import joeq.Class.jq_Type;

public class LocalAccessPath extends AccessPath {

	public LocalAccessPath(AbsMemLoc base, FieldElem field, int Id) {
		super(base, field, Id);
	}

	public AbsMemLoc getBase() {
		return this.base;
	}

	public FieldElem getField() {
		return this.field;
	}

	public int getId() {
		return Id;
	}

	// find the left-most location, which is the root
	// for AccessPath, the root must be ParamElem or StaticElem
	public StackObject findRoot() {

		if (base instanceof StackObject) {
			assert (base instanceof ParamElem || base instanceof StaticElem) : "the root"
					+ " of an AccessPath must be either a ParamElem or StaticElem!";
			if (base instanceof ParamElem) {
				return (ParamElem) base;
			} else if (base instanceof StaticElem) {
				return (StaticElem) base;
			} else {
				assert false : "the root MUST be a ParamElem or StaticElem!";
				return null;
			}
		} else if (base instanceof HeapObject) {
			assert (base instanceof LocalAccessPath) : "the base of an AccessPath must be an AccessPath"
					+ " if the base is a heap object.";
			if (base instanceof LocalAccessPath) {
				return ((LocalAccessPath) base).findRoot();
			} else {
				assert false : "the base of an AccessPath must be an AccessPath"
						+ " if the base is a heap object.";
			}
		} else {
			assert false : "The base of " + this
					+ " is not legal (unknown mem loc)";
		}
		return null;
	}

	@Override
	public boolean equals(Object other) {
		return (other instanceof LocalAccessPath)
				&& (base.equals(((LocalAccessPath) other).base))
				&& (field.equals(((LocalAccessPath) other).field));
	}

	@Override
	public String toString() {
		return base + "." + field;
	}

	@Override
	public String dump() {
		return base + "." + field;
	}

	@Override
	public int hashCode() {
		return 37 * base.hashCode() + field.hashCode();
	}

	@Override
	public boolean hasFieldSelector(FieldElem f) {
		return field.equals(f) || base.hasFieldSelector(f);
	}

	@Override
	public int countFieldSelector(FieldElem f) {
		return base.countFieldSelector(f) + (field.equals(f) ? 1 : 0);
	}

	@Override
	public boolean hasFieldType(jq_Type type) {
		return this.type.equals(type) || base.hasFieldType(type);
	}

	@Override
	public boolean hasFieldTypeComp(jq_Type type) {
		return this.type.isSubtypeOf(type) || type.isSubtypeOf(this.type)
				|| base.hasFieldTypeComp(type);
	}

	// get the prefix ending with field f which is also an AccessPath
	// ONLY AccessPath has this getPrefix method
	// ONLY when hasFieldSelector returns true, you can call this method
	// this method return the prefix which is the right-most access path
	// e.g. a.\e.f.g.f.k will return a.\e.f.g.f if we want to get f field
	@Override
	public LocalAccessPath getPrefix(FieldElem f) {
		assert hasFieldSelector(f) : this
				+ " does NOT have field selector "
				+ f
				+ " (you can only call getPrefix method when hasFieldSelector returns true)";
		if (field.equals(f)) {
			return this;
		}
		return ((LocalAccessPath) base).getPrefix(f);
	}

	@Override
	public LocalAccessPath getTypePrefix(jq_Type type) {
		assert hasFieldType(type);
		if (this.type.equals(type)) {
			return this;
		}
		return ((LocalAccessPath) base).getTypePrefix(type);
	}

	@Override
	public LocalAccessPath getTypeCompPrefix(jq_Type type) {
		assert hasFieldTypeComp(type);
		if (this.type.isSubtypeOf(type) || type.isSubtypeOf(this.type)) {
			return this;
		}
		return ((LocalAccessPath) base).getTypeCompPrefix(type);
	}

	// try to find the prefix ending with f
	// if not found, return null
	// this method does the same thing as the one above
	@Override
	public LocalAccessPath findPrefix(FieldElem f) {
		if (field.equals(f)) {
			return this;
		} else if (base instanceof LocalAccessPath) {
			return ((LocalAccessPath) base).getPrefix(f);
		}
		return null;
	}
}
