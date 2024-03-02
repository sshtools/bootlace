import com.sshtools.bootlace.api.AppRepository.AppRepositoryBuilder;
import com.sshtools.bootlace.api.LayerContext;
import com.sshtools.bootlace.api.LocalRepository.LocalRepositoryBuilder;
import com.sshtools.bootlace.api.Plugin;
import com.sshtools.bootlace.api.PluginContext;
import com.sshtools.bootlace.api.RemoteRepository.RemoteRepositoryBuilder;
import com.sshtools.bootlace.platform.LayerContextImpl;
import com.sshtools.bootlace.platform.PluginContextProviderImpl;

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
}