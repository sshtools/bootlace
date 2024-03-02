package com.sshtools.bootlace.api;

import java.util.List;
import java.util.Set;

public interface PluginLayer  extends Layer {

	Set<ArtifactRef> artifacts();
	
	List<PluginRef> pluginRefs();

}