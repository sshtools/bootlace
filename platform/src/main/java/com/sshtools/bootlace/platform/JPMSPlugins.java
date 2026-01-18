package com.sshtools.bootlace.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

import com.sshtools.bootlace.api.DependencyGraph;
import com.sshtools.bootlace.api.Plugin;

final class JPMSPlugins  {
	private final List<JPMSNode> list;
	
	JPMSPlugins(ModuleLayer layer) {
		 list = ServiceLoader.load(layer, Plugin.class).
				 stream().
				 map(p -> new JPMSNode(p, this)).
				 toList();
		
	}
	
	List<JPMSNode> sorted() {
		var topologicallySorted = new ArrayList<>(new DependencyGraph<>(list).getTopologicallySorted());
		list.forEach(l -> { 
			if(!topologicallySorted.contains(l)) {
				topologicallySorted.add(l);
			}
		});
		return  topologicallySorted;
	}
	
	Optional<JPMSNode> forModule(String req) {
		return list.stream().filter(p -> p.getProvider().type().getModule().getName().equals(req)).findFirst();
	}
}