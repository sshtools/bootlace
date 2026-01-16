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
import java.util.Arrays;
import java.util.LinkedHashSet;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResult;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolverException;
import org.sonatype.inject.Description;

import com.sshtools.jini.INI;
import com.sshtools.jini.INIParseException;

/**
 * Generates a bootlace plugin from the current project
 */
@Mojo(threadSafe = true, name = "generate-layer-configuration", requiresProject = true, defaultPhase = LifecyclePhase.GENERATE_RESOURCES, requiresDependencyResolution = ResolutionScope.RUNTIME, requiresDependencyCollection = ResolutionScope.RUNTIME)
@Description("Generates the layers.ini resource")
public class LayerConfigurationGeneratePluginMojo extends AbstractExtensionsMojo {

	protected static final String SEPARATOR = "/";

	@Parameter(defaultValue = "true", property = "readExisting")
	private boolean readExisting = true;

	@Parameter(defaultValue = "true", property = "alwaysWritePOMValues")
	private boolean alwaysWritePOMValues = true;
	
	@Parameter(defaultValue = "${basedir}/src/main/resources/META-INF/layers.ini", required = true)
	private File source;
	
	@Parameter(defaultValue = "${project.build.outputDirectory}/META-INF/layers.ini", required = true)
	private File target;
	
	@Parameter(defaultValue = "STATIC", required = true)
	private String type;
	
	@Parameter(defaultValue = "${project.groupId}.${project.artifactId}", required = true)
	private String id;
	
	@Parameter(defaultValue = "false")
	private boolean repositories;
	
	@Component
	private MavenProjectHelper projectHelper;

	protected void onExecute() throws MojoExecutionException, MojoFailureException {
		var log = getLog();
		
		
		try {
			INI ini;
			boolean changed;
			if(source.exists() && readExisting) {
				ini = createINIReader().build().read(source.toPath());
				changed = false;
			}
			else {
				ini = INI.create();
				changed  = true;
			}
			
			var sec = ini.obtainSection("component");
			
			var thisId = id;
			if(thisId.equals("")) {
				thisId = project.getGroupId() + "-" + project.getArtifactId();
			}
			thisId = thisId.replaceAll("-", ".");
			if(!sec.contains("id") || alwaysWritePOMValues) {
				sec.put("id", thisId);
				changed = true;
			}
			
			if(!sec.contains("name") || sec.get("name").equals("<unnamed>") || alwaysWritePOMValues) {
				sec.put("name", project.getName() == null ? "<unnamed>" : project.getName());
				changed = true;
			}

			if(!sec.contains("type") || sec.get("type", "").equals("") || (!type.equals("STATIC") && !type.equals(sec.get("type", ""))) || alwaysWritePOMValues) {
				sec.put("type", type.toUpperCase());
				changed = true;
			}


			var parents = new LinkedHashSet<String>();
			var artifacts = new LinkedHashSet<String>();
			var allArtifacts = getFilteredDependencies();
			var extensions = getExtensions(allArtifacts);
			
			if(repositories) {
				var repoList = getRepositories();
				if(!repoList.isEmpty()) {
					var repos = ini.obtainSection("repository");
					repoList.forEach(repo -> {
						if(repo.getLayout().getId().equals("default")) {
							var repoSec = repos.obtainSection("remote").obtainSection(repo.getId());
							repoSec.put("name", repo.getId());
							repoSec.put("root", repo.getUrl());
							repoSec.put("releases", repo.getReleases().isEnabled());
							repoSec.put("snapshots", repo.getSnapshots().isEnabled());
						}
						else {
							log.warn("Ignoring repository "+ repo.getId() + ", it is of layout " + repo.getLayout().getId());
						}
					});
				}
			}
			
			if(!sec.get("type").equals("ROOT")) {
				for(var art : allArtifacts) {
					
					if(isBootlaceProvided(art.getFile())) {
						log.info("Skipping bootlace provided "  + art.getFile());
						continue;
					} 
					
					if (isExclude(art)) {
						log.info("Artifact " + art.getFile() + " is excluded");
						continue;
					} 
					
					if(isExtension(art.getFile())) {
						var artId = getLayers(art.getFile()).section("component").get("id");
						var isInOther = false;
						for(var innerArt : allArtifacts) {
							if(!innerArt.equals(art) && isExtension(innerArt.getFile())) {
								var innerLayer = getLayers(innerArt.getFile());
								if(Arrays.asList(innerLayer.section("component").getAllElse("parent")).contains(artId)) {
									isInOther = true;
									break;
								}
							}
						}
						if(!isInOther) {
							parents.add(artId);
						}
					}
					else if(!isTransientDependencyOfExtension(art, extensions)) {
						artifacts.add(makeArtifactName(art));
					}
				}
			}
			
			String[] newParents = parents.toArray(new String[0]);
			String[] oldParents = sec.getAllElse("parent");
			if( ( oldParents.length == 0 || alwaysWritePOMValues ) && !Arrays.equals(newParents, oldParents)) {
				if(newParents.length == 0)
					sec.remove("parent");
				else
					sec.putAll("parent", newParents);
				changed = true;
			}

			
			String[] newArtifacts = artifacts.toArray(new String[0]);
			var artifactsSec  = ini.obtainSection("artifacts");
			var oldArtifacts = artifactsSec.keys().toArray(new String[0]);
			if( ( oldArtifacts.length == 0 || alwaysWritePOMValues ) && !Arrays.equals(newArtifacts, oldArtifacts)) {
				if(newArtifacts.length == 0) {
					log.info("Removing artifacts section");
					artifactsSec.remove();
				}
				else {
					log.info("Adding artifacts section back");
					artifactsSec.clear();
					for(var art : newArtifacts) {
						artifactsSec.put(art, (String)null);
					}
				}
				changed = true;
			}	
			else if(newArtifacts.length == 0 && oldArtifacts.length == 0) {
				artifactsSec.remove();
				changed = true;
			}
			
			var meta = ini.obtainSection("meta");
			if(!meta.contains("description") || alwaysWritePOMValues) {
				meta.put("description", project.getDescription() == null ? sec.get("name") : project.getDescription());
				changed = true;
			}
			meta.put("artifact", project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion());
			if(!meta.contains("artifact") || alwaysWritePOMValues) {
				meta.put("artifact", project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion());
				changed = true;
			}
			
			if(changed || !target.exists()) {
				log.info("Writing layer configuration file " + target);
				checkDir(target.toPath().getParent());
				createINIWriter().build().write(ini, target.toPath());
			}
		}
		catch(IOException | INIParseException ioe) {
			throw new MojoExecutionException("Failed to update layers.ini.", ioe);
		}
	}

	@Override
	protected void doHandleResult(ArtifactResult result)
			throws MojoExecutionException, DependencyResolverException, ArtifactResolverException, IOException {
		/* Only used for extra artifacts */
	}
}