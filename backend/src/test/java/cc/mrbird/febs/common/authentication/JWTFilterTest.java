package cc.mrbird.febs.common.authentication;

import cc.mrbird.febs.common.properties.FebsProperties;
import cc.mrbird.febs.common.utils.SpringContextUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JWTFilterTest {
    private GenericApplicationContext context;
    private final JWTFilter filter = new JWTFilter();

    @Before
    public void setUp() {
        FebsProperties properties = new FebsProperties();
        properties.getShiro().setAnonUrl("/login,/actuator/health,/user/check/**");
        context = new GenericApplicationContext();
        context.getBeanFactory().registerSingleton("febsProperties", properties);
        context.refresh();
        new SpringContextUtil().setApplicationContext(context);
    }

    @After
    public void tearDown() {
        new SpringContextUtil().setApplicationContext(null);
        context.close();
    }

    private boolean allowed(String contextPath, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath(contextPath);
        request.setRequestURI(contextPath + path);
        return filter.isAccessAllowed(request, new MockHttpServletResponse(), null);
    }

    @Test
    public void anonymousRoutesWorkAtRootAndUnderTomcatContext() {
        for (String contextPath : new String[]{"", "/febs"}) {
            assertTrue(allowed(contextPath, "/login"));
            assertTrue(allowed(contextPath, "/actuator/health"));
            assertTrue(allowed(contextPath, "/user/check/alice"));
        }
    }

    @Test
    public void protectedAndSimilarRoutesStillRequireAuthentication() {
        for (String contextPath : new String[]{"", "/febs"}) {
            assertFalse(allowed(contextPath, "/user"));
            assertFalse(allowed(contextPath, "/actuator/env"));
            assertFalse(allowed(contextPath, "/actuator/health/details"));
            assertFalse(allowed(contextPath, "/loginExtra"));
        }
    }
}
