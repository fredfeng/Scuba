package framework.scuba.domain;

import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import joeq.Class.jq_Type;

public class StaticElem extends StackObject {

	// the class this static field is declared in
	protected jq_Class clazz;

	// the field this static element denotes
	protected jq_Field field;

	public StaticElem(jq_Class clazz, jq_Field field) {
		this.clazz = clazz;
		this.field = field;
		this.length = 1;
		this.type = field.getType();
	}

	@Override
	public StaticElem findRoot() {
		return this;
	}

	@Override
	public boolean equals(Object other) {
		return (other instanceof StaticElem)
				&& (clazz.equals(((StaticElem) other).clazz))
				&& (field.equals(((StaticElem) other).field));
	}

	@Override
	public int hashCode() {
		return 37 * clazz.hashCode() + field.hashCode();
	}

	@Override
	public String toString() {
		return "[SC]" + clazz + "_" + field;
	}

	@Override
	public String dump() {
		return "[SC]" + clazz + "_" + field;
	}

	@Override
	public boolean hasFieldSelector(FieldElem field) {
		return false;
	}

	@Override
	public int countFieldSelector(FieldElem field) {
		return 0;
	}

	@Override
	public boolean hasFieldType(jq_Type type) {
		return false;
	}

	@Override
	public boolean hasFieldTypeComp(jq_Type type) {
		return false;
	}
}
