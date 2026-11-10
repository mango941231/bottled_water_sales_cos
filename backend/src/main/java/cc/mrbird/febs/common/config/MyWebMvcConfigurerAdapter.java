package cc.mrbird.febs.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MyWebMvcConfigurerAdapter implements WebMvcConfigurer {

    /**
     * 图片/文件访问目录，与 febs.uploadPath 保持一致。
     * 配置为绝对路径后可避免部署到 Linux 后访问不到图片
     */
    @Value("${febs.uploadPath}")
    private String uploadPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = uploadPath.endsWith("/") ? uploadPath : uploadPath + "/";
        registry.addResourceHandler("/imagesWeb/**").addResourceLocations("file:" + location);
        WebMvcConfigurer.super.addResourceHandlers(registry);
    }
}
