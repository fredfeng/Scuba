package framework.scuba.domain;

public interface AccessPath {

	public StackObject getBase();

	public AccessPathElem getPrefix(FieldElem f);

	public int countFieldSelector(FieldElem field);

}