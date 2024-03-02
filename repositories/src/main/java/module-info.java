import com.sshtools.bootlace.api.AppRepository;
import com.sshtools.bootlace.api.LocalRepository;
import com.sshtools.bootlace.api.RemoteRepository;
import com.sshtools.bootlace.repositories.AppRepositoryImpl;
import com.sshtools.bootlace.repositories.LocalRepositoryImpl;
import com.sshtools.bootlace.repositories.MavenRemoteRepositoryImpl;

module com.sshtools.bootlace.repositories {
	requires transitive com.sshtools.bootlace.api;
	requires transitive java.net.http;
	
	exports com.sshtools.bootlace.repositories;
	
	provides LocalRepository.LocalRepositoryBuilder with 
		LocalRepositoryImpl.LocalRepositoryBuilder;
	provides AppRepository.AppRepositoryBuilder with
		AppRepositoryImpl.AppRepositoryBuilder;
	provides RemoteRepository.RemoteRepositoryBuilder with
		MavenRemoteRepositoryImpl.RemoteRepositoryBuilder;
}