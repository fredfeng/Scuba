package framework.scuba.domain;

import joeq.Class.jq_Type;

public class ArrayAllocElem extends AllocElem {

	final protected int dim;

	public ArrayAllocElem(Alloc allocSite, Context context, jq_Type type) {
		super(allocSite, context, type);
		this.dim = 1;
	}

	public ArrayAllocElem(Alloc allocSite, Context context, int dim,
			jq_Type type) {
		super(allocSite, context, type);
		this.dim = dim;
	}

	public int getDim() {
		return dim;
	}

	public boolean isArray() {
		return (dim == 1);
	}

	public boolean isMultiArray() {
		return (dim > 1);
	}

	@Override
	public String toString() {
		return alloc + "||[D]" + dim + "||" + context;
	}

	@Override
	public boolean equals(Object other) {
		return (other instanceof ArrayAllocElem)
				&& (alloc.equals(((ArrayAllocElem) other).alloc))
				&& (context.equals(((ArrayAllocElem) other).context))
				&& (dim == ((ArrayAllocElem) other).dim);
	}

	@Override
	public int hashCode() {
		return 37 * alloc.hashCode() + context.hashCode() + dim * 7;
	}

}