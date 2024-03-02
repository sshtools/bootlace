package com.sshtools.bootlace.platform;

import static java.lang.String.format;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.sshtools.bootlace.api.BootContext;
import com.sshtools.bootlace.api.ChildLayer;
import com.sshtools.bootlace.api.RootLayer;
import com.sshtools.bootlace.api.Http.HttpClientFactory;
import com.sshtools.bootlace.platform.AbstractLayer.AbstractLayerBuilder;
import com.sshtools.jini.INI;

public final class RootLayerBuilder extends AbstractLayerBuilder<RootLayerBuilder> {

	Optional<BootContext> appContext = Optional.empty();
	Optional<String> userAgent = Optional.empty();
	List<ChildLayer> layers = new ArrayList<>();
	Optional<HttpClientFactory> httpClientFactory = Optional.empty();
	Optional<BootstrapRepository> bootstrapRepository = Optional.empty();

	RootLayerBuilder() {
		this("_app_");
	}

	RootLayerBuilder(String id) {
		super(id);
	}

	public RootLayer build() {
		return new RootLayerImpl(this);
	}

	@Override
	public RootLayerBuilder fromDescriptor(Descriptor descriptor) {
		super.fromDescriptor(descriptor);
		var l = descriptor.componentSection();
		descriptor.artifactsSection().ifPresent(a -> {
			throw new IllegalArgumentException("The root layer may have no artifacts.");
		});
		withUserAgent(l.getOr("userAgent"));
		descriptor.layerSections().forEach(g -> {
			var type = g.getOr("type").orElse("static");
			if (type.equals("dynamic")) {
				withLayers(new DynamicLayer.Builder(g.key()).
						fromComponentSection(g).
						build());
			} else if (type.equals("static")) {
				withLayers(new PluginLayerImpl.Builder(g.key()).
						fromComponentSection(g).
						fromArtifactsSection(g.sectionOr("artifacts")).
						build());
			} else
				throw new IllegalArgumentException("Unknown layer type " + type);
		});
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

	@Override
	protected RootLayerBuilder fromComponentSection(INI.Section section) {
		super.fromComponentSection(section);
		if (!"static".equals(section.getOr("type").orElse("static"))) {
			throw new IllegalArgumentException(format("Layer {0} cannot be of type {1}", id, section.get("type")));
		}
		return this;
	}
}