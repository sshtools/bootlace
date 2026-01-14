import com.sshtools.bootlace.api.Plugin;

import springtest.mod1.Mod1Plugin;

open module springtest.mod1 {
	requires transitive springtest.api;
	requires transitive com.sshtools.bootlace.api;
	
	provides Plugin with Mod1Plugin;
	exports springtest.mod1 to spring.beans;
	
}