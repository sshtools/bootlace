module com.sshtools.bootlace.spring {
	requires transitive spring.beans;
	requires transitive spring.context;
	requires transitive spring.core;
	requires transitive jakarta.annotation;
	requires transitive com.sshtools.bootlace.platform;
	opens com.sshtools.bootlace.spring to  spring.core;
	
	exports com.sshtools.bootlace.spring;
	
}