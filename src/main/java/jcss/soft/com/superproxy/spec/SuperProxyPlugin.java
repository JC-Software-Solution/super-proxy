package jcss.soft.com.superproxy.spec;

import lombok.Data;
import java.util.Map;

@Data
public class SuperProxyPlugin {
    private String name;
    private Map<String, Object> config;
}
