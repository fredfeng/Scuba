package framework.scuba.helper;

import framework.scuba.domain.Context;

public class ContextHelper {

	public static Context putTogether(Context first, Context second) {
		Context ret = first.clone();
		ret.appendEnd(second);
		return ret;
	}
}
