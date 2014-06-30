package framework.scuba.domain;

import joeq.Class.jq_Type;

public class StaticAccessPath extends AccessPath {

	public StaticAccessPath(AbsMemLoc base, FieldElem field, int Id) {
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
	public StaticElem findRoot() {

		if (base instanceof StackObject) {
			assert (base instanceof StaticElem) : "the root can only be StaticElem!";
			if (base instanceof ParamElem) {
				assert false : "the root can only be StaticElem!";
			} else if (base instanceof StaticElem) {
				return (StaticElem) base;
			} else {
				assert false : "the root MUST a StaticElem!";
				return null;
			}
		} else if (base instanceof HeapObject) {
			assert (base instanceof StaticAccessPath) : ""
					+ "the base can only be StaticAccessPath.";
			if (base instanceof LocalAccessPath) {
				assert false : "the base can only be StaticAccessPath";
			} else if (base instanceof StaticAccessPath) {
				return ((StaticAccessPath) base).findRoot();
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
		return (other instanceof StaticAccessPath)
				&& (base.equals(((StaticAccessPath) other).base))
				&& (field.equals(((StaticAccessPath) other).field));
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

	// get the prefix ending with field f which is also an AccessPath
	// ONLY AccessPath has this getPrefix method
	// ONLY when hasFieldSelector returns true, you can call this method
	public StaticAccessPath getPrefix(FieldElem f) {

		assert hasFieldSelector(f) : this
				+ " does NOT have field selector "
				+ f
				+ " (you can only call getPrefix method when hasFieldSelector returns true)";

		if (field.equals(f)) {
			return this;
		}

		return ((StaticAccessPath) base).getPrefix(f);
	}

	// try to find the prefix ending with f
	// if not found, return null
	public StaticAccessPath findPrefix(FieldElem f) {

		if (field.equals(f)) {
			return this;
		} else if (base instanceof StaticAccessPath) {
			return ((StaticAccessPath) base).getPrefix(f);
		}

		return null;
	}

}
