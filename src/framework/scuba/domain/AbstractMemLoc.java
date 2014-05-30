package framework.scuba.domain;

public abstract class AbstractMemLoc {

	public static enum ArgDerivedType {
		IS_ARG_DERIVED, NOT_ARG_DERIVED, UN_KNOWN;
	}

	ArgDerivedType argDerived;

	abstract public AbstractMemLoc findRoot();

	public void setArgDerived() {
		this.argDerived = ArgDerivedType.IS_ARG_DERIVED;
	}

	public void resetArgDerived() {
		this.argDerived = ArgDerivedType.NOT_ARG_DERIVED;
	}

	public boolean knownArgDerived() {
		return (this.argDerived != ArgDerivedType.UN_KNOWN);
	}

	public boolean isArgDerived() {
		return (this.argDerived == ArgDerivedType.IS_ARG_DERIVED);
	}

	public boolean hasFieldSelector(FieldElem f) {
		if (!(this instanceof AccessPath)) {
			return false;
		}

		return false;
	}

	@Override
	abstract public boolean equals(Object other);

	@Override
	abstract public int hashCode();

	@Override
	abstract public String toString();
}
