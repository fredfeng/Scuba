package framework.scuba.domain;

import java.util.HashSet;
import java.util.Set;

import chord.program.Program;
import joeq.Class.jq_Array;
import joeq.Class.jq_Type;

public abstract class AccessPath extends HeapObject {

	protected Set<FieldElem> smashed = new HashSet<FieldElem>();

	final protected AbsMemLoc base;

	final protected FieldElem field;

	protected int Id;

	public AccessPath(AbsMemLoc base, FieldElem field, int Id) {
		this.base = base;
		this.field = field;
		this.Id = Id;
		// when creating an AccessPath, add the field into the fields set of the
		// base because the base has such a field
		base.addField(field);
		length = base.length + 1;
		if (field instanceof NormalFieldElem) {
			this.type = ((NormalFieldElem) field).getField().getType();
		} else if (field instanceof EpsilonFieldElem) {
			this.type = base.getType();
		} else if (field instanceof IndexFieldElem) {
			// assert (base.getType() instanceof jq_Array) :
			// "for access path with index field "
			// + "base must be jq_Array!";
			if (base.getType() instanceof jq_Array) {
				this.type = ((jq_Array) base.getType()).getElementType();
			} else {
				this.type = Program.g().getClass("java.lang.Object");
			}

		} else {
			assert false : "wired things!";
		}
	}

	abstract public AbsMemLoc getBase();

	abstract public FieldElem getField();

	abstract public int getId();

	abstract public StackObject findRoot();

	abstract public AccessPath getPrefix(FieldElem f);

	abstract public AccessPath findPrefix(FieldElem f);

	protected boolean isSmashed;

	public void setSmashed() {
		isSmashed = true;
	}

	public void resetSmashed() {
		isSmashed = false;
	}

	public boolean isSmashed() {
		return isSmashed;
		// return !smashed.isEmpty();
	}

	public void addSmashedField(FieldElem f) {
		assert (f instanceof NormalFieldElem || f instanceof IndexFieldElem) : ""
				+ "only normal field and index field can be smashed!";
		smashed.add(f);
	}

	public void addSmashedFields(Set<FieldElem> fields) {
		for (FieldElem f : fields) {
			assert (f instanceof NormalFieldElem || f instanceof IndexFieldElem) : ""
					+ "only normal field and index field can be smashed!";
		}
		smashed.addAll(fields);
	}

	public Set<FieldElem> getSmashedFields() {
		return smashed;
	}

	// only when the ap has f can this method be called.
	public Set<FieldElem> getPreSmashedFields(FieldElem f) {
		assert hasFieldSelector(f) : "getSmashedFields(f) can only "
				+ "be called when it is a smashed access path!";
		Set<FieldElem> ret = new HashSet<FieldElem>();
		ret.addAll(smashed);
		addFieldAsSmashed(f, ret);
		return ret;
	}

	private void addFieldAsSmashed(FieldElem f, Set<FieldElem> set) {
		if (field.equals(f)) {
			set.add(f);
			return;
		}
		set.add(field);
		assert (base instanceof AccessPath);
		((AccessPath) base).addFieldAsSmashed(f, set);
		return;
	}
}
