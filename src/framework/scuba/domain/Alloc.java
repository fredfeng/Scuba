package framework.scuba.domain;

import joeq.Class.jq_Type;
import joeq.Compiler.Quad.Quad;

public class Alloc {

	// the class this new instruction generates
	final protected jq_Type type;

	// call site
	final protected Quad allocSite;

	public Alloc(jq_Type type, Quad allocSite) {
		this.type = type;
		this.allocSite = allocSite;
	}

	@Override
	public boolean equals(Object other) {
		return (other instanceof Alloc) && (type.equals(((Alloc) other).type));
	}

	public jq_Type getType() {
		return this.type;
	}

	public Quad getAllocSite() {
		return allocSite;
	}

	@Override
	public int hashCode() {
		return type.hashCode();
	}

	@Override
	public String toString() {
		return "[A]" + type.getName() + " " + allocSite + " " + allocSite.getMethod();
	}

}
