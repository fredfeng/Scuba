package framework.scuba.domain;

public interface FieldSelectable {

	public boolean hasFieldSelector(FieldElem field);

	public int countFieldSelector(FieldElem field);

}
