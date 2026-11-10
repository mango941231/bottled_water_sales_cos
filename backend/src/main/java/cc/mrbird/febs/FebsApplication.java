package cc.mrbird.febs;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
@EnableScheduling
@EnableAsync
public class FebsApplication extends SpringBootServletInitializer {

    /**
     * 部署到外部 Tomcat 时由 Servlet 容器调用此方法启动 Spring 容器
     */
    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(FebsApplication.class);
    }

    public static void main(String[] args) {
        new SpringApplicationBuilder(FebsApplication.class)
                .run(args);
    }
}