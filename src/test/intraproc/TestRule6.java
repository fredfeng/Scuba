package test.intraproc;

/**
 * Test case for Rule 4 in figure 8.
 * 
 * @author yufeng
 * 
 */
public class TestRule6 {

	class Z {
	}

	// if-else.
	Z foo(Z a) {
		int i = 1;
		Z v, u, ret;
		if (i > 0) {
			v = new Z();
			ret = v;
		} else {
			u = new Z();
			ret = u;
		}

		return ret;
	}
}
