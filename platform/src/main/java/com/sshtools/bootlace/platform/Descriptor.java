package com.sshtools.bootlace.platform;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.sshtools.bootlace.api.Exceptions;
import com.sshtools.bootlace.api.Zip;
import com.sshtools.jini.INI;
import com.sshtools.jini.INI.Section;

public final class Descriptor {

	public final static class Builder {
		private INI ini = INI.create();

		public Builder fromINI(INI ini) {
			this.ini = ini;
			return this;
		}

		public Builder fromArtifact(Path path) throws IOException {
			if(Files.isDirectory(path)) {
				try {
					return fromINI(Bootlace.createINIReader().build().read(path.resolve(DESCRIPTOR_RESOURCE_NAME)));
				} catch(NoSuchFileException nsfe) {
					throw new Exceptions.NotALayer(path);
				} catch (ParseException e) {
					throw new IllegalStateException("Failed to parse.", e);
				}
			}
			else {
				return fromINI(loadDescriptor(path));
			}
		}

		private INI loadDescriptor(Path path) throws IOException {
			try (var in = Files.newInputStream(path)) {
				return loadDescriptor(in).orElseThrow(() -> new Exceptions.NotALayer(path));
			}
		}

		private Optional<INI> loadDescriptor(InputStream in) throws IOException {
			return Zip.unzip(in, (entry, zin) -> {
				if (Zip.isArchive(entry)) {
					try {
						return loadDescriptor(zin);
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				} else {
					try {
						return Optional.of(Bootlace.createINIReader().build().read(new InputStreamReader(zin)));
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					} catch (ParseException e) {
						throw new IllegalStateException("Failed to parse descriptor.", e);
					}
				}
			}, f -> Zip.isArchive(f) || f.getName().equals(DESCRIPTOR_RESOURCE_NAME));
		}

		public Descriptor build() {
			return new Descriptor(this);
		}
	}

	public static final String DESCRIPTOR_RESOURCE_NAME = "layers.ini";

	private final Section component;
	private final String id;
	private final List<Section> children;
	private final Optional<Section> artifacts;

	private Descriptor(Builder bldr) {
		component = bldr.ini.section("component");
		artifacts = bldr.ini.sectionOr("artifacts");
		id = component.get("id");
		children = Arrays.asList(bldr.ini.sectionOr("layer").map(l -> l.allSections()).orElse(new Section[0]));
	}

	public List<Section> layerSections() {
		return children;
	}

	public Section componentSection() {
		return component;
	}

	public String id() {
		return id;
	}

	public Optional<Section> artifactsSection() {
		return artifacts;
	}
}
