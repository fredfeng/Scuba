package test.interproc;

//Example to demo subtyping.
public class TestInter3 {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		new TestInter3().run();
	}
	
	void run() {
		Y y = new Y();
		y.foo();//invoke X:foo
		
		Z z = new Z();
		z.foo();//invoke Z:foo
		
		X z1 = new Z();
		z1.foo();//invoke X:foo, but actual is Z:foo
		
		//for u = v.foo(), get we get it's lhs?
		X x = y.bar();
		I i = x;
		
		this.dump();
		i.inter();
	}
	
	private void dump() {
		
	}
	
	//Z <: Y <: X
	class X implements I{
		void foo() {
			
		}
		
		public void inter() {
			
		}
		
		X bar(){
			return new X();
		}
	}
	
	class Y extends X{

	}
	
	class Z extends Y {
		void foo() {
			
		}
	}
	
	interface I {
		public void inter();
	}

}
