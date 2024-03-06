import com.sshtools.bootlace.api.AppRepository;
import com.sshtools.bootlace.api.AppRepository.AppRepositoryBuilder;
import com.sshtools.bootlace.api.ConfigResolver;
import com.sshtools.bootlace.api.LayerContext;
import com.sshtools.bootlace.api.LocalRepository;
import com.sshtools.bootlace.api.LocalRepository.LocalRepositoryBuilder;
import com.sshtools.bootlace.api.Plugin;
import com.sshtools.bootlace.api.PluginContext;
import com.sshtools.bootlace.api.RemoteRepository;
import com.sshtools.bootlace.api.RemoteRepository.RemoteRepositoryBuilder;
import com.sshtools.bootlace.platform.AppRepositoryImpl;
import com.sshtools.bootlace.platform.ConfigResolverImpl;
import com.sshtools.bootlace.platform.LayerContextImpl;
import com.sshtools.bootlace.platform.LocalRepositoryImpl;
import com.sshtools.bootlace.platform.PluginContextProviderImpl;
import com.sshtools.bootlace.platform.RemoteRepositoryImpl;

module com.sshtools.bootlace.platform {
	requires transitive com.sshtools.bootlace.api;
	requires transitive com.sshtools.jini;
	exports com.sshtools.bootlace.platform;
	requires transitive java.net.http;
	uses Plugin;
	uses LocalRepositoryBuilder;
	uses RemoteRepositoryBuilder;
	uses AppRepositoryBuilder;
	
	uses LayerContext.Provider;
	provides LayerContext.Provider with LayerContextImpl.Provider;
	provides PluginContext.Provider with PluginContextProviderImpl;
	
	provides LocalRepository.LocalRepositoryBuilder with 
		LocalRepositoryImpl.LocalRepositoryBuilder;
	provides AppRepository.AppRepositoryBuilder with
		AppRepositoryImpl.AppRepositoryBuilder;
	provides RemoteRepository.RemoteRepositoryBuilder with
		RemoteRepositoryImpl.RemoteRepositoryBuilder;
	
	provides ConfigResolver with ConfigResolverImpl;
}