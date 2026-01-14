import com.sshtools.bootlace.api.Plugin;

import springtest.api.Api;

open module springtest.api {
	requires transitive com.sshtools.bootlace.api;
	requires transitive jakarta.annotation;
	requires transitive spring.context;
	requires transitive spring.beans;
	exports springtest.api;
	
	provides Plugin with Api;
}