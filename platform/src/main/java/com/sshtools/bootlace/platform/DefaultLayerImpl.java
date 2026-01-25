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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import com.sshtools.bootlace.api.ArtifactRef;
import com.sshtools.bootlace.api.GAV;
import com.sshtools.bootlace.api.Layer;
import com.sshtools.bootlace.api.LayerArtifacts;
import com.sshtools.bootlace.api.DefaultLayer;
import com.sshtools.bootlace.api.PluginRef;
import com.sshtools.bootlace.api.Zip;
import com.sshtools.bootlace.platform.jini.INI.Section;
import com.sshtools.bootlace.platform.jini.INIParseException;

public final class DefaultLayerImpl extends AbstractChildLayer implements DefaultLayer {
	public final static class Builder extends AbstractChildLayerBuilder<DefaultLayerImpl.Builder> {
		private final Set<ArtifactRef> artifacts = new LinkedHashSet<>();
		private Optional<String> icon = Optional.empty();
		private Optional<String> description = Optional.empty();
		private Path baseDir = Paths.get(System.getProperty("user.dir"));

		public Builder(String id) {
			super(id);
		}

		public DefaultLayerImpl build() {
			return new DefaultLayerImpl(this);
		}
		
		@Override
		public DefaultLayerImpl.Builder fromDescriptor(Descriptor descriptor) {
			descriptor.meta().ifPresent(meta -> {
				meta.getOr("description").ifPresent(this::withDescription);
				meta.getOr("icon").ifPresent(this::withIcon);
			});
			return super.fromDescriptor(descriptor);
		}


		public DefaultLayerImpl.Builder withBaseDirectory(Path baseDir) {
			this.baseDir = baseDir;
			return this;
		}

		public DefaultLayerImpl.Builder withArtifactsDirectory(Path dir) {
			try (var str = Files.newDirectoryStream(dir,
						f -> ( !Files.isDirectory(f) && f.getFileName().toString().toLowerCase().endsWith(".jar")) || 
							 (  Files.isDirectory(f) && f.getFileName().toString().equals("classes")))) {
				
				for (var path : str) {
					if(Files.isDirectory(path)) {
						withArtifactRefs(ArtifactRef.of(GAV.ofSpec(getArtifactFromLayersINI(path))).withPath(path));						
					}
					else {
						withJarArtifacts(path);
					}
				}
			} catch (IOException ioe) {
				throw new UncheckedIOException(ioe);
			}
			return withBaseDirectory(dir);
		}
		
		public DefaultLayerImpl.Builder withJarArtifacts(Path... paths) {
			Arrays.asList(paths).forEach((path) -> {
				try(var in = Files.newInputStream(path)) {
					try {
						withArtifactRefs(refFromProperties(getMavenPropertiesForArtifact(in)).withPath(path));
					}
					catch(IllegalArgumentException iae) {
						/* Might be Maven supplied  jar without any maven data. First encountered
						 * with javafx platform jars (e.g. with `linux` classifier).   
						 */
						withArtifactRefs(refFromFilename(path).withPath(path));
						
					}
					
					
				} catch (IOException ioe) {
					throw new UncheckedIOException(ioe);
				}	
			});
			return this;
		}
		
		public DefaultLayerImpl.Builder withIcon(String icon) {
			this.icon = Optional.of(icon);
			return this;
		}
		
		public DefaultLayerImpl.Builder withDescription(String description) {
			this.description = Optional.of(description);
			return this;
		}
		
		public DefaultLayerImpl.Builder withArtifactJar(InputStream in) {
			try {
				var props = getMavenPropertiesForArtifact(in);
				withArtifactRefs(refFromProperties(props));
				return this;
			}
			catch(IOException ioe) {
				throw new UncheckedIOException(ioe);
			}
		}

		private ArtifactRef refFromProperties(Properties props) {
			return ArtifactRef.of(GAV.ofParts(
				props.getProperty("groupId"),
				props.getProperty("artifactId"),
				props.getProperty("version")
			));
		}

		private ArtifactRef refFromFilename(Path props) {
			var fname = props.getFileName().toString();
			if(fname.toLowerCase().endsWith(".jar")) {
				fname = fname.substring(0, fname.length() - 4);
			}
			return ArtifactRef.of(GAV.ofSpec(fname).normalizeJar());
		}
		
		private String getArtifactFromLayersINI(Path path) throws IOException {
			var dir = path.resolve("META-INF").resolve("layers.ini");
			if(Files.exists(dir)) {
				try {
					var ini = Bootlace.createINIReader().build().read(dir);
					return ini.obtainSection("meta").get("artifact");
				} catch (INIParseException e) {
					throw new IllegalArgumentException("Failed to parse .ini.", e);
				} 
			}
			throw new IOException("Not an extension maven artifact (no layers.ini).");
		}

		@SuppressWarnings("unused")
		private Properties getMavenPropertiesForArtifact(InputStream in) throws IOException {
			return Zip.unzip(in, (ze, is) -> {
				var p = new Properties();
				try {
					p.load(is);
					return Optional.of(p);
				}
				catch(IOException ioe) {
					throw new UncheckedIOException(ioe);
				}
			}, ze -> ze.getName().matches(".*META-INF/maven/[^/]+/[^/]+/pom\\.properties")).orElseThrow(() -> new IllegalArgumentException("Artifact does not appear to be a Maven artifact, there is no Maven meta-data."));
		}

		public DefaultLayerImpl.Builder withArtifactRefs(Collection<ArtifactRef> ref) {
			this.artifacts.addAll(ref);
			return this;
		}
		public DefaultLayerImpl.Builder withArtifactRefs(ArtifactRef... refs) {
			return withArtifactRefs(Arrays.asList(refs));
		}

		public DefaultLayerImpl.Builder withArtifacts(Collection<GAV> ref) {
			return withArtifactRefs(ref.stream().map(ArtifactRef::of).toList());
		}

		public DefaultLayerImpl.Builder withArtifacts(GAV... gav) {
			return withArtifacts(Arrays.asList(gav));
		}

		public DefaultLayerImpl.Builder withArtifacts(String... gav) {
			return withArtifacts(Arrays.asList(gav).stream().map((s) -> new GAV.Builder().ofSpec(s).build()).toList());
		}

		protected DefaultLayerImpl.Builder fromArtifactsSection(Optional<Section> section) {
			section.ifPresent(this::fromArtifactsSection);
			return this;
			
		}
		
		protected DefaultLayerImpl.Builder fromArtifactsSection(Section section) {
			section.values().forEach((k, v) -> {
				if(v.length == 1 && !v[0].equals("")) {
					withArtifactRefs(ArtifactRef.of(GAV.ofSpec(k), baseDir.resolve(v[0])));
				}
				else if(v.length == 0 || (v.length == 1 && v[0].equals(""))) {
					withArtifactRefs(ArtifactRef.of(GAV.ofSpec(k)));
				}
			});
			return this;
		}
	}

	protected final Set<ArtifactRef> artifacts;
	
	final List<PluginRef> pluginRefs = new ArrayList<>();
	Optional<LayerArtifacts> layerArtifacts = Optional.empty();
	Set<Layer> publicLayers = new LinkedHashSet<>(); 
	private final Optional<String> icon, description;

	DefaultLayerImpl(DefaultLayerImpl.Builder layerBuilder) {
		super(layerBuilder);
		artifacts = new LinkedHashSet<>(layerBuilder.artifacts);
		icon = layerBuilder.icon;
		description = layerBuilder.description;
	}

	@Override
	public Optional<String> description() {
		return description;
	}

	@Override
	public Optional<String> icon() {
		return icon;
	}

	@Override
	public List<PluginRef> pluginRefs() {
		return Collections.unmodifiableList(pluginRefs);
	}

	@Override
	public Optional<LayerArtifacts> finalArtifacts() {
		return layerArtifacts;
	}

	@Override
	public  Set<ArtifactRef> artifacts() {
		return artifacts;
	}

	@Override
	public String toString() {
		return "DefaultLayerImpl [id()=" + id() + ", name()=" + name() + ", parents()="
				+ parents() + ", appRepositories()=" + appRepositories() + ", localRepositories()="
				+ localRepositories() + ", remoteRepositories()=" + remoteRepositories() + ", artifacts=" + artifacts + "]";
	}

	void addParent(DefaultLayerImpl layer) {
		throw new UnsupportedOperationException("TODO");
		
	}

}