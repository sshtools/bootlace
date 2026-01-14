import com.sshtools.bootlace.api.Plugin;

import springtest.mod2.Mod2Plugin;

module springtest.mod2 {
	requires transitive springtest.api;
	requires spring.beans;
	requires spring.context;
	requires com.sshtools.bootlace.api;
	
	provides Plugin with Mod2Plugin;
	exports springtest.mod2;
	
	opens springtest.mod2;
	
}