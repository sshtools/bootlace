package com.sshtools.bootlace.platform;

import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.function.Consumer;

import com.sshtools.bootlace.api.NodeModel;
import com.sshtools.bootlace.api.Plugin;
import com.sshtools.bootlace.api.DependencyGraph.Dependency;

final class JPMSNode implements NodeModel<JPMSNode> {
	
	private final ServiceLoader.Provider<Plugin> provider;
	private final JPMSPlugins plugins;
	
	JPMSNode(Provider<Plugin> provider, JPMSPlugins plugins) {
		super();
		this.provider = provider;
		this.plugins = plugins;
	}

	ServiceLoader.Provider<Plugin> getProvider() {
		return provider;
	}

	@Override
	public String name() {
		return provider.type().getName();
	}

	@Override
	public String toString() {
		return name() + " [" + provider.type().getModule().getName() + "]";
	}

	@Override
	public void dependencies(Consumer<Dependency<JPMSNode>> model) {
		var desc = provider.type().getModule().getDescriptor();
		desc.requires().stream().forEach(req -> {
			plugins.forModule(req.name()).ifPresent(node -> {
				model.accept(new Dependency<JPMSNode>(node, this));
				node.dependencies(model);
			});	
		});
	}
	
}