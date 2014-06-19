package framework.scuba.domain;

public class LocalAccessPath extends AccessPath {

	// base can be an AccessPath, a ParamElem or a StaticElem
	// i.e., AccessPath is a recursive data structure
	final protected AbsMemLoc base;

	final protected FieldElem field;

	protected int Id;

	public LocalAccessPath(AbsMemLoc base, FieldElem field, int Id) {
		this.base = base;
		this.field = field;
		this.Id = Id;
		// when creating an AccessPath, add the field into the fields set of the
		// base because the base has such a field
		base.addField(field);
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

	// get the prefix ending with field f which is also an AccessPath
	// ONLY AccessPath has this getPrefix method
	// ONLY when hasFieldSelector returns true, you can call this method
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

	// try to find the prefix ending with f
	// if not found, return null
	public LocalAccessPath findPrefix(FieldElem f) {
		if (field.equals(f)) {
			return this;
		} else if (base instanceof LocalAccessPath) {
			return ((LocalAccessPath) base).getPrefix(f);
		}
		return null;
	}

}
