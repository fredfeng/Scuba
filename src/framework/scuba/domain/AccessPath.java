package framework.scuba.domain;

import java.util.HashSet;
import java.util.Set;

public abstract class AccessPath extends HeapObject {

	// we do this for smashing
	protected boolean smashed = false;

	protected Set<FieldElem> rep = new HashSet<FieldElem>();

	abstract public AbsMemLoc getBase();

	abstract public FieldElem getField();

	abstract public int getId();

	abstract public StackObject findRoot();

	abstract public AccessPath getPrefix(FieldElem f);

	abstract public AccessPath findPrefix(FieldElem f);

}
