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
package com.sshtools.bootlace.mavenplugin;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResult;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolverException;
import org.sonatype.inject.Description;

/**
 * Generates a bootlace plugin from the current project
 */
@Mojo(threadSafe = true, name = "generate-app", requiresProject = true, defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.RUNTIME, requiresDependencyCollection = ResolutionScope.RUNTIME)
@Description("Generates the layers.ini resource")
public class AppGeneratePluginMojo extends AbstractExtensionsMojo {

	@Parameter(defaultValue = "${basedir}/extensions", required = true)
	private File target;
	
	@Component
	private MavenProjectHelper projectHelper;

	protected void onExecute() throws MojoExecutionException, MojoFailureException {
		var log = getLog();
//		
//		var file = new File(project.getBasedir(), location);
//		
		try {
			var rootProject = this.project;
			while(true) {
				var p = rootProject.getParent();
				if(p == null || !p.getFile().isFile())
					break;
				else
					rootProject = p;
			}
			
			if(rootProject == null) {
				log.warn("Cannot generate app, cannot determine root project.");
			}
			else {
				log.info("Root project is " + rootProject.getFile());
				linkOrCopyExtensions(rootProject);
			}
		}
		catch(IOException ioe) {
			throw new MojoExecutionException("Failed to update layers.ini.", ioe);
		}
	}

	private void linkOrCopyExtensions(MavenProject project) throws IOException {
		var log = getLog();
		var out = new File(project.getBuild().getOutputDirectory());
		if(!isExclude(project.getArtifact()) && isExtension(out)) {
			var lyrs = getLayers(out);
			var cmp = lyrs.obtainSection("component");
			var id = cmp.get("id");
			var type = cmp.get("type", "STATIC");
			if("STATIC".equalsIgnoreCase(type) || "GROUP".equals(type)) {
				log.info("Adding extension " + id + " of type " + type + " as a developer extension");
				
				/* Link to the modules classes directory */
				var dir = target.toPath().resolve(id);
				if(Files.exists(dir))
					recursiveDelete(dir);
				Files.createDirectories(dir);
				Files.createSymbolicLink(dir.resolve("classes"), out.toPath());
				
				/* And link to all the modules dependencies (those that would otherwise be 
				 * included in the .zip 
				 */
				var allArtifacts = getFilteredDependencies(project);
				var extensions = getExtensions(allArtifacts);
				
				for(var art : allArtifacts) {
					if(isBootlaceProvided(art.getFile())) {
						log.info("Skipping bootlace provided "  + art.getFile());
						continue;
					} 
					
					if (isExclude(art)) {
						log.info("Artifact " + art.getFile() + " is excluded");
						continue;
					} 
					
					if(!isExtension(art.getFile()) && !isTransientDependencyOfExtension(art, extensions)) {

						Files.createSymbolicLink(dir.resolve(makeFilename(art)), art.getFile().toPath());
					}
				}
			}
		}
		for(var module : project.getCollectedProjects()) {
			linkOrCopyExtensions(module);
		}
	}

	@Override
	protected void doHandleResult(ArtifactResult result)
			throws MojoExecutionException, DependencyResolverException, ArtifactResolverException, IOException {
		/* Only used for extra artifacts */
	}

	public static void recursiveDelete(Path fileOrDirectory, FileVisitOption... options) {
		try (var walk = Files.walk(fileOrDirectory, options)) {
			walk.sorted(Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.delete(p);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		} 
		catch(IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
	}
}