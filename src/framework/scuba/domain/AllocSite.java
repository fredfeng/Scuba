package framework.scuba.domain;

import joeq.Class.jq_Type;

public class AllocSite {

	// the class this new instruction generates
	protected jq_Type type;

	public AllocSite(jq_Type type) {
		this.type = type;
	}

	@Override
	public boolean equals(Object other) {
		return (other instanceof AllocSite)
				&& (type.equals(((AllocSite) other).type));
	}

	@Override
	public int hashCode() {
		return type.hashCode();
	}

	@Override
	public String toString() {
		return "[Alloc] " + type;
	}
}
