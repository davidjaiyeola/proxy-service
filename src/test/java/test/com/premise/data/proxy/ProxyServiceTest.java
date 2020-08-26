package test.com.premise.data.proxy;

import com.premise.data.proxy.ProxyServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;



@SpringBootTest(classes = ProxyServiceApplication.class)
public class ProxyServiceTest {
    @Test
    public void whenSpringContextIsBootstrapped_thenNoExceptions() {
    }
}
