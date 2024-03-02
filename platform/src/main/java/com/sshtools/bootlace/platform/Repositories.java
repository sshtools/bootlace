package com.sshtools.bootlace.platform;

import java.io.File;
import java.util.Optional;
import java.util.ServiceLoader;

import com.sshtools.bootlace.api.LayerContext;
import com.sshtools.bootlace.api.LocalRepository;
import com.sshtools.bootlace.api.Logs;
import com.sshtools.bootlace.api.Logs.BootLog;
import com.sshtools.bootlace.api.Logs.Log;
import com.sshtools.bootlace.api.Repository;
import com.sshtools.bootlace.api.Repository.RepositoryBuilder;

public class Repositories {

	private final static Log LOG = Logs.of(BootLog.RESOLUTION);

//	
	private static class LazyBootstrapRepository {
		static BootstrapRepository DEFAULT = (BootstrapRepository)new BootstrapRepository.BootstrapRepositoryBuilder().build();
	}

	public static BootstrapRepository bootstrapRepository() {
		return LazyBootstrapRepository.DEFAULT;
	}

	@SuppressWarnings("unchecked")
	public static <BLDR extends RepositoryBuilder<BLDR, REPO>, REPO extends Repository> Optional<REPO> forIdOr(ModuleLayer layer, 
			String id, Class<? extends REPO> repoClass, Class<? extends BLDR> builderClass) {
		
		LOG.debug("Resolve repository of type ''{0}'' of class ''{1}'' in ''{2}''", id, repoClass.getName(), layer);
		if(id.equals("bootstrap") && repoClass.equals(LocalRepository.class)) {
			return Optional.of((REPO)bootstrapRepository());
		}
		else {
			return (Optional<REPO>) builderForId(layer, id, builderClass).map((bldr) -> bldr.build());
		}
	}

	public static <BLDR extends RepositoryBuilder<BLDR, ? extends REPO>, REPO extends Repository> Optional<BLDR> builderForId(ModuleLayer layer, 
			String id, Class<? extends BLDR> bldrClass) {
		for (var repo : ServiceLoader.load(layer, bldrClass)) {
			if (repo.id().equals(id))
				return Optional.of((BLDR) repo);
		}
		
		LOG.debug("NO repository builder of type ''{0}'' of class ''{1}'' in child layers of ''{2}'', trying service layers", id, bldrClass.getName(), layer);
		
		var layerCtx = LayerContext.get(bldrClass);
		for(var lyr : layerCtx.globalLayers()) {
			
			LOG.debug("Trying layer ''{0}''", lyr); 
			for (var repo : ServiceLoader.load(lyr, bldrClass)) {
				if (repo.id().equals(id))
					return Optional.of((BLDR) repo);
			}
			
				LOG.debug("Still NO repository builder of type ''{0}'' of class ''{1}'' in service layers, giving up", id, bldrClass.getName());		
		}
		
		return Optional.empty();
	}

	static String dottedToPath(String dotted) {
		return dotted.replace('.', File.separatorChar);
	}

}
