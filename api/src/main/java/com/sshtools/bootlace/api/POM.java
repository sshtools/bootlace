package com.sshtools.bootlace.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public final class POM {
	
	private record ScopeVersion(String version, Optional<String> scope) {}

	public static POM of(Path path) {
		try (var in = Files.newInputStream(path)) {
			return of(in);
		} catch (IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
	}

	public static POM of(InputStream in) {
		return new POM(in);
	}

	private final GAV gav;
	private final Set<GAV> dependencies;

	private POM(InputStream in) {
		
		var project = XML.of(in);
		var parentXml = project.child("parent");

		/* GAV */
		var artifactId = project.value("artifactId")
				.orElseThrow(() -> new IllegalArgumentException("POM has no artifactId"));
		var groupId = project.value("groupId")
				.orElseGet(() -> parentXml
						.orElseThrow(() -> new IllegalStateException("No groupId, and no parent.")).value("groupId")
						.orElseThrow(() -> new IllegalArgumentException("No groupId, and no groupId in parent.")));
		var version = project.value("version")
				.orElseGet(() -> parentXml
						.orElseThrow(() -> new IllegalStateException("No version, and no parent.")).value("version")
						.orElseThrow(() -> new IllegalArgumentException("No version, and no version in parent.")));
		gav = GAV.ofParts(groupId, artifactId, version);
		
		
		/* Managed Dependencies */
		var managed = new HashMap<GAV, ScopeVersion>();
		project.child("dependencyManagement").ifPresent(mgEl ->
			mgEl.child("dependencies").ifPresent(depsEl ->
				depsEl.children().forEach(depEl -> {
				
					var fullGav = new GAV.Builder().
						withArtifactId(depEl.value("artifactId").orElseThrow(() -> new IllegalStateException("Dependency has no artifact."))).
						withGroupId(depEl.value("groupId")).
						withVersion(depEl.value("version")).build();
						
					managed.put(fullGav.toWithoutVersion(), new ScopeVersion(fullGav.version(), depEl.value("scope")));
				})
			)
		);
		
		/* Dependencies */
		var deps = new LinkedHashSet<GAV>();
		project.child("dependencies").ifPresent(depsEl ->
			depsEl.children().forEach(depEl -> {
				var partialGav = new GAV.Builder().
					withArtifactId(depEl.value("artifactId").orElseThrow(() -> new IllegalStateException("Dependency has no artifact."))).
					withGroupId(depEl.value("groupId")).build();

				var scope = depEl.value("scope").orElseGet(() -> Optional.ofNullable(managed.get(partialGav)).map(sv -> sv.scope.orElse("")).orElse("")  );
				if(scope.equals("test") || scope.equals("provided")) {
					return;
				}
				
				deps.add(partialGav.toWithVersion(
					depEl.value("version").or(() -> Optional.ofNullable(managed.get(partialGav)).map(ScopeVersion::version))
				));
			})
		);
		
		dependencies = Collections.unmodifiableSet(deps);

	}
	
	public GAV gav() {
		return gav;
	}
	
	public Set<GAV> dependencies() {
		return dependencies;
	}

	@Override
	public String toString() {
		return "POM [gav=" + gav + ", dependencies=" + dependencies + "]";
	}
}
