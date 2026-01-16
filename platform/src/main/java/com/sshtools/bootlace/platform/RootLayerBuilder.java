/**
 * Copyright © 2023 JAdaptive Limited (support@jadaptive.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the “Software”), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies
 * or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.sshtools.bootlace.platform;

import static java.lang.String.format;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.sshtools.bootlace.api.ArtifactVersion;
import com.sshtools.bootlace.api.BootContext;
import com.sshtools.bootlace.api.ChildLayer;
import com.sshtools.bootlace.api.Http.HttpClientFactory;
import com.sshtools.bootlace.api.LayerType;
import com.sshtools.bootlace.api.PluginContext.PluginHostInfo;
import com.sshtools.bootlace.api.RootLayer;
import com.sshtools.bootlace.platform.AbstractLayer.AbstractLayerBuilder;
import com.sshtools.bootlace.platform.jini.INI;

public final class RootLayerBuilder extends AbstractLayerBuilder<RootLayerBuilder> {

	Optional<BootContext> appContext = Optional.empty();
	Optional<String> userAgent = Optional.empty();
	List<ChildLayer> layers = new ArrayList<>();
	Optional<HttpClientFactory> httpClientFactory = Optional.empty();
	Optional<BootstrapRepository> bootstrapRepository = Optional.empty();
	PluginHostInfo pluginHostInfo = new PluginHostInfo("bootlace", ArtifactVersion.getVersion("com.sshtools", "bootlace-platform"), "bootlace");
	Optional<PluginInitializer> pluginInitializer = Optional.empty();
	Optional<PluginDestroyer> pluginDestroyer = Optional.empty();
	
	RootLayerBuilder() {
		this("_app_");
		type = LayerType.ROOT;
	}

	RootLayerBuilder(String id) {
		super(id);
	}

	public RootLayer build() {
		return new RootLayerImpl(this);
	}

	public RootLayerBuilder fromStandardArguments(String... args) {
		if(args.length == 0) 
			fromINIResource();
		else if(args.length == 1 && !args[0].startsWith("--")) {
			fromINI(args[0]);
		}
		else 
			throw new IllegalArgumentException("A single argument is supported, the path to a layers.ini file.");
		
		return this;
	}

	@Override
	public RootLayerBuilder fromDescriptor(Descriptor descriptor) {
		super.fromDescriptor(descriptor);
		var l = descriptor.component();
		descriptor.artifacts().ifPresent(a -> {
			throw new IllegalArgumentException("The root layer may have no artifacts.");
		});
		withUserAgent(l.getOr("user-agent"));
		descriptor.layers().forEach(g -> {
			var type = g.getEnum(LayerType.class, "type", LayerType.STATIC);
			if (type == LayerType.DYNAMIC) {
				withLayers(new ExtensionLayerImpl.Builder(g.key()).
						fromComponentSection(g).
						build());
			} else if (type == LayerType.STATIC || type == LayerType.GROUP || type == LayerType.BOOT) {
				withLayers(new PluginLayerImpl.Builder(g.key()).
						fromComponentSection(g).
						fromArtifactsSection(g.sectionOr("artifacts")).
						build());
			} else
				throw new IllegalArgumentException("Unknown layer type " + type);
		});
		return this;
	}

	public RootLayerBuilder withPluginHostInfo(PluginHostInfo pluginHostInfo) {
		this.pluginHostInfo = pluginHostInfo;
		return this;
	}

	public RootLayerBuilder withBootstrapRepository(BootstrapRepository bootstrapRepository) {
		this.bootstrapRepository = Optional.of(bootstrapRepository);
		return this;
	}

	public RootLayerBuilder withContext(BootContext appContext) {
		this.appContext = Optional.of(appContext);
		return this;
	}

	public RootLayerBuilder withHttpClientFactory(HttpClientFactory httpClientFactory) {
		this.httpClientFactory = Optional.of(httpClientFactory);
		return this;
	}

	public RootLayerBuilder withLayers(ChildLayer... layer) {
		return withLayers(Arrays.asList(layer));
	}

	public RootLayerBuilder withLayers(Collection<ChildLayer> layer) {
		layer.forEach(l -> layers.stream().filter(a -> l.id().equals(a.id())).findFirst().ifPresent(
				f -> new IllegalStateException(MessageFormat.format("Layer with id {0} already exists.", f.id()))));
		this.layers.addAll(layer);
		return this;
	}

	public RootLayerBuilder withUserAgent(Optional<String> userAgent) {
		this.userAgent = userAgent;
		return this;
	}

	public RootLayerBuilder withUserAgent(String userAgent) {
		this.userAgent = Optional.of(userAgent);
		return this;
	}

	public RootLayerBuilder withPluginInitialiser(PluginInitializer pluginWrapper) {
		this.pluginInitializer = Optional.of(pluginWrapper);
		return this;
	}

	public RootLayerBuilder withPluginDestroyer(PluginDestroyer pluginDestroyer) {
		this.pluginDestroyer = Optional.of(pluginDestroyer);
		return this;
	}

	@Override
	protected RootLayerBuilder fromComponentSection(INI.Section section) {
		super.fromComponentSection(section);
		if (type != LayerType.ROOT) {
			throw new IllegalArgumentException(format("Layer {0} cannot be of type {1}", id, this.type));
		}
		return this;
	}
}