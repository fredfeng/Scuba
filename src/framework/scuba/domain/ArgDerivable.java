package framework.scuba.domain;

public interface ArgDerivable {

	public static enum ArgDvdType {
		IS_ARG_DERIVED, NOT_ARG_DERIVED, UN_KNOWN;
	}

	public void setArgDvd();

	public void resetArgDvd();

	public ArgDvdType getArgDvdMarker();

	public boolean knownArgDvd();

	public boolean unknowArgDvd();

	public boolean isArgDvd();

	public boolean isNotArgDvd();

}