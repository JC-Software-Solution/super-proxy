package jcss.soft.com.superproxy;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

// Exclude the java-operator-sdk auto-configuration during unit tests because
// the testing runtime here doesn't include the fabric8 testing extension
// (SupportTestingClient) required by OperatorAutoConfiguration.
@SpringBootTest(properties = "spring.autoconfigure.exclude=io.javaoperatorsdk.operator.springboot.starter.OperatorAutoConfiguration")
class SuperproxyApplicationTests {

    @MockBean
    private KubernetesClient kubernetesClient;

    @Test
    void contextLoads() {
    }

}