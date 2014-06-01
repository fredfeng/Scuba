package test.intraproc;

/**
 * Test case for long access path.
 * 
 * @author yufeng
 * 
 */
public class TestRule5 {

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
		
		Z z = new Z();
		y.z = z;
		x = y;
		x.z.g = new G();
		X x1 = x;
		
		G g = x1.z.g;
		G g1 = x1.z.g.g;
		
	}
}
