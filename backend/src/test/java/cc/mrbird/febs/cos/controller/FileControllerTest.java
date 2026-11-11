package cc.mrbird.febs.cos.controller;

import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class FileControllerTest {
    @Test
    public void uploadDirectoryIsInjectedFromConfigurationWithoutAStringBean() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "test", Collections.<String, Object>singletonMap("febs.uploadPath", "/var/lib/febs/upload")));
            context.register(FileController.class);
            context.refresh();
            assertEquals("/var/lib/febs/upload", ReflectionTestUtils.getField(
                    context.getBean(FileController.class), "uploadPath"));
        }
    }
}
