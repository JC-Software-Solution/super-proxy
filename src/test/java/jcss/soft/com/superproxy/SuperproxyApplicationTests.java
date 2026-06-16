package jcss.soft.com.superproxy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Exclude the java-operator-sdk auto-configuration during unit tests because
// the testing runtime here doesn't include the fabric8 testing extension
// (SupportTestingClient) required by OperatorAutoConfiguration.
@SpringBootTest(properties = "spring.autoconfigure.exclude=io.javaoperatorsdk.operator.springboot.starter.OperatorAutoConfiguration")
class SuperproxyApplicationTests {

	@Test
	void contextLoads() {
	}

}
