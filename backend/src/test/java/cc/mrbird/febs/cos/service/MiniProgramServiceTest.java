package cc.mrbird.febs.cos.service;

import cc.mrbird.febs.cos.dao.MiniProgramBannerMapper;
import cc.mrbird.febs.cos.entity.MiniProgramBanner;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.junit.Before;
import org.junit.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class MiniProgramServiceTest {
    private MiniProgramService service;
    private JdbcTemplate jdbc;

    @Before
    public void setUp() throws Exception {
        DriverManagerDataSource source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE mini_program_banner (id BIGINT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(100), image_url VARCHAR(1000), sort_num INT, status INT, create_time TIMESTAMP, update_time TIMESTAMP)");
        MybatisConfiguration configuration = new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(MiniProgramBannerMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(source); factory.setConfiguration(configuration);
        service = new MiniProgramService(new SqlSessionTemplate(factory.getObject()).getMapper(MiniProgramBannerMapper.class));
    }

    @Test
    public void appShowsOnlyEnabledBannersInStableConfiguredOrder() {
        MiniProgramBanner last = service.save(banner("last", "https://example.com/last.jpg", 20, 1));
        service.save(banner("disabled", "https://example.com/off.jpg", 0, 0));
        MiniProgramBanner first = service.save(banner("first", "banner-image.png", 10, 1));
        MiniProgramBanner tie = service.save(banner("tie", "https://example.com/tie.jpg", 10, 1));
        assertEquals(Arrays.asList(first.getId(), tie.getId(), last.getId()), service.banners(true).stream().map(MiniProgramBanner::getId).collect(Collectors.toList()));
        assertEquals(4, service.banners(false).size());
    }

    @Test
    public void adminEditsImageOrderAndStatusAndCanDeleteAllWithoutFallback() {
        MiniProgramBanner saved = service.save(banner("old", "https://example.com/old.jpg", 20, 1));
        Date created = saved.getCreateTime();
        MiniProgramBanner edit = banner(" new ", " banner-new.png ", 0, 0);
        edit.setId(saved.getId()); edit.setCreateTime(new Date(0));
        service.save(edit);
        assertEquals(0, service.banners(true).size());
        MiniProgramBanner result = service.banners(false).get(0);
        assertEquals("new", result.getTitle()); assertEquals("banner-new.png", result.getImageUrl());
        assertTrue(Math.abs(created.getTime() - result.getCreateTime().getTime()) < 1000);
        service.delete(saved.getId()); assertTrue(service.banners(false).isEmpty());
        expectInvalid(() -> service.delete(saved.getId()));
    }

    @Test
    public void rejectsMissingUnsafeOrInvalidConfigurationBeforePersisting() {
        for (String url : Arrays.asList("", "javascript:alert(1)", "data:image/png;base64,aaa", "../secret.png", "https://user:password@example.com/a.jpg", "https://", "file:///a.png"))
            expectInvalid(() -> service.save(banner("test", url, 0, 1)));
        expectInvalid(() -> service.save(banner("test", "ok.png", -1, 1)));
        expectInvalid(() -> service.save(banner("test", "ok.png", 0, 9)));
        MiniProgramBanner nonexistent = banner("test", "ok.png", 0, 1); nonexistent.setId(999L);
        expectInvalid(() -> service.save(nonexistent));
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("SELECT COUNT(*) FROM mini_program_banner", Integer.class));
    }

    private MiniProgramBanner banner(String title, String image, int order, int status) {
        MiniProgramBanner value = new MiniProgramBanner(); value.setTitle(title); value.setImageUrl(image); value.setSortNum(order); value.setStatus(status); return value;
    }
    private void expectInvalid(Runnable action) {
        try { action.run(); fail("Expected invalid banner"); } catch (IllegalArgumentException expected) { assertNotNull(expected.getMessage()); }
    }
}
