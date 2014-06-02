package test.intraproc;

/**
 * Test case for long access path.
 * 
 * @author yufeng
 * 
 */
public class TestRule8 {

	class Z {
		G g;
	}
	
	class G {
		G g = new G();
	}
	
	class X {
		Z z;
	}

	class Y extends X {
	}
	
	// long access path.
	void foo(Z a) {
		Y y = new Y();
		X x = new X();
		
		Z z = x.z;
		y.z = null;
		Z z1 = y.z;
	}
}
