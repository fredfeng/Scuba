package framework.scuba.domain;

public class AccessPath extends HeapObject {

	protected AbstractMemLoc base;

	protected FieldElem field;

	public AccessPath(AbstractMemLoc base, FieldElem field) {
		this.base = base;
		this.field = field;
	}

	public AbstractMemLoc getBase() {
		return this.base;
	}

	public FieldElem getField() {
		return this.field;
	}

	public AbstractMemLoc findRoot() {

		if (base instanceof StackObject) {
			return (StackObject) base;
		} else if (base instanceof HeapObject) {

			if (base instanceof AllocElem) {
				return (AllocElem) base;
			} else if (base instanceof AccessPath)
				return ((AccessPath) base).findRoot();

		} else {
			assert false : "The base of " + this + " is not legal";
		}

		return null;
	}

	public String toString() {
		return base + "." + field;
	}
}
