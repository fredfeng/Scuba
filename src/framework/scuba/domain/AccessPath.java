package framework.scuba.domain;

public abstract class AccessPath extends HeapObject {

	// we do this for smashing
	protected boolean smashed = false;

	abstract public AbsMemLoc getBase();

	abstract public FieldElem getField();

	abstract public int getId();

	abstract public StackObject findRoot();

	abstract public AccessPath getPrefix(FieldElem f);

	abstract public AccessPath findPrefix(FieldElem f);

}
