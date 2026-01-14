package springtest.api;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.sshtools.bootlace.api.Plugin;

import jakarta.annotation.PostConstruct;

@Component
public class Api implements Plugin {
	
	@Autowired
	private SharedService sharedService;
//
//	@Autowired
//	private final List<ReplyProducer> replies;
	
//	@Autowired
//	public Api(List<ReplyProducer> replies) {
//	    this.replies = replies;
//	}
	
	@PostConstruct
	private void setup() {
		System.out.println("Api initialised, shared service say " + sharedService.getUuid());
	}
	
	public String appSays() {
		return "I am an Api";
	}

}
