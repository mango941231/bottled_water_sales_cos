package cc.mrbird.febs.cos.controller;

import cc.mrbird.febs.common.utils.R;
import cc.mrbird.febs.cos.service.ICommodityInfoService;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;

import static org.junit.Assert.*;

public class GoodsDetailTest {
    private WebController controllerReturning(LinkedHashMap<String, Object> goods) throws Exception {
        ICommodityInfoService service = (ICommodityInfoService) Proxy.newProxyInstance(
                ICommodityInfoService.class.getClassLoader(), new Class<?>[]{ICommodityInfoService.class},
                (proxy, method, args) -> {
                    assertEquals("goodsDetail", method.getName());
                    assertEquals(Integer.valueOf(3), args[0]);
                    return goods;
                });
        Constructor<?> constructor = WebController.class.getConstructors()[0];
        Object[] dependencies = Arrays.stream(constructor.getParameterTypes())
                .map(type -> type == ICommodityInfoService.class ? service : null).toArray();
        return (WebController) constructor.newInstance(dependencies);
    }

    @Test
    public void productCodeCannotOverwriteResponseStatus() throws Exception {
        LinkedHashMap<String, Object> goods = new LinkedHashMap<>();
        goods.put("id", 3);
        goods.put("code", "COMM-1794796570127");
        goods.put("price", new BigDecimal("25.00"));
        goods.put("orderNum", 2);
        goods.put("images", "SA1794800099078.png");
        R response = controllerReturning(goods).goodsDetail(3);
        assertEquals(0, response.get("code"));
        assertSame(goods, response.get("data"));
        assertFalse(response.containsKey("price"));
    }

    @Test
    public void missingProductReturnsBusinessErrorInsteadOfNullPointerException() throws Exception {
        R response = controllerReturning(null).goodsDetail(3);
        assertEquals(404, response.get("code"));
        assertFalse(response.containsKey("data"));
    }
}
