package springtest.api;

import java.util.UUID;

import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class SharedServiceImpl implements SharedService {

	private final static UUID id = UUID.randomUUID();
	
	@PostConstruct
	private void setup() {
		System.out.println("Share server construction id = " + id);
	}
	
	@Override
	public String getUuid() {
		return id.toString();
	}

}
