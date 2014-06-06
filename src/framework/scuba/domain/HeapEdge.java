package framework.scuba.domain;

public class HeapEdge {

	protected AbstractMemLoc src;

	protected HeapObject dst;

	protected FieldElem field;

	public HeapEdge(AbstractMemLoc src, HeapObject dst, FieldElem field) {
		this.src = src;
		this.dst = dst;
		this.field = field;
	}

	public AbstractMemLoc getSrc() {
		return src;
	}

	public HeapObject getDst() {
		return dst;
	}

	public FieldElem getField() {
		return field;
	}

	@Override
	public boolean equals(Object other) {
		return (other instanceof HeapEdge)
				&& (src.equals(((HeapEdge) other).src))
				&& (dst.equals(((HeapEdge) other).dst));
	}

	@Override
	public int hashCode() {
		return 37 * 37 * src.hashCode() + 37 * dst.hashCode()
				+ field.hashCode();
	}

	@Override
	public String toString() {
		return "(" + src + "," + field + "->" + dst + ")";
	}
}
