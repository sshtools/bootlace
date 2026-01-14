package springtest.mod2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.sshtools.bootlace.api.Plugin;
import com.sshtools.bootlace.api.PluginContext;

@Component
public class Mod2Plugin implements Plugin {
	
	@Autowired
	private Mod2 mod2;

	@Override
	public void afterOpen(PluginContext context) throws Exception {
		System.out.println("Opened plugin Mod2 - " + mod2.getReply());
	}

}
