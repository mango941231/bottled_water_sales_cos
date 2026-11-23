package cc.mrbird.febs.cos.controller;

import cc.mrbird.febs.cos.entity.*;
import cc.mrbird.febs.cos.service.*;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.Assert.*;

public class OrderCheckoutTest {
    private WebController controller;
    private AddressInfo address;
    private OrderInfo cart;
    private OrderInfo saved;
    private OrderDetail savedDetail;
    private Wrapper<?> detailUpdate;
    private int consumed;
    private Wrapper<?> orderUpdate;

    @Before
    public void setUp() throws Exception {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "checkout-test");
        for (Class<?> entity : Arrays.asList(OrderInfo.class, OrderDetail.class, AddressInfo.class)) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
        address = new AddressInfo().setId(17).setUserId(3).setDefaultAddress(1);
        cart = new OrderInfo().setId(10).setCode("TEST-CART").setUserId(3)
                .setCommodityId(5).setOrderStatus(0);
        CommodityInfo goods = new CommodityInfo().setId(5)
                .setOnPut("1").setPrice(new BigDecimal("12.50")).setStockNum(100);
        Map<Class<?>, Object> dependencies = new HashMap<>();
        dependencies.put(IAddressInfoService.class, service(IAddressInfoService.class, (method, args) -> address));
        dependencies.put(ICommodityInfoService.class, service(ICommodityInfoService.class, (method, args) -> goods));
        dependencies.put(IOrderInfoService.class, service(IOrderInfoService.class, (method, args) -> {
            if (method.equals("getById")) return cart;
            if (method.equals("update")) { orderUpdate = (Wrapper<?>) args[0]; return true; }
            if (method.equals("save") || method.equals("updateById")) {
                saved = (OrderInfo) args[0];
                return true;
            }
            throw new AssertionError(method);
        }));
        dependencies.put(IOrderDetailService.class, service(IOrderDetailService.class, (method, args) -> {
            if (method.equals("save")) savedDetail = (OrderDetail) args[0];
            else if (method.equals("update")) detailUpdate = (Wrapper<?>) args[0];
            else throw new AssertionError(method);
            return true;
        }));
        dependencies.put(WaterTicketService.class, new WaterTicketService(null, null, null, null, null) {
            @Override
            public int consume(Integer userId, Integer commodityId, int requested, String code) {
                consumed += requested;
                return requested;
            }
        });
        Constructor<?> constructor = WebController.class.getConstructors()[0];
        Object[] args = Arrays.stream(constructor.getParameterTypes()).map(dependencies::get).toArray();
        controller = (WebController) constructor.newInstance(args);
    }

    @Test
    public void checkoutUsesUnboundProductAndCurrentAddress() {
        Map<String, Object> line = new HashMap<>();
        line.put("orderId", 10);
        line.put("commodityNum", 4);
        line.put("ticketNum", 2);
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", 3);
        payload.put("lines", Collections.singletonList(line));
        controller.completeCart(payload);
        assertEquals(Integer.valueOf(3), saved.getUserId());
        assertEquals(Integer.valueOf(OrderStatus.PENDING_DELIVERY), saved.getOrderStatus());
        assertEquals(Integer.valueOf(17), saved.getAddressId());
        assertEquals(Integer.valueOf(4), saved.getCommodityNum());
        assertEquals(Integer.valueOf(2), saved.getTicketNum());
        assertEquals(new BigDecimal("25.00"), saved.getOrderPrice());
        assertEquals(2, consumed);
        assertNotNull(detailUpdate);
        assertTrue(detailUpdate.getSqlSet().contains("address_id"));
        assertTrue(detailUpdate.getSqlSegment().contains("user_id"));
    }

    @Test
    public void directPurchaseAcceptsUnboundProduct() {
        OrderInfo purchase = new OrderInfo().setCommodityId(5).setUserId(3)
                .setAddressId(17).setCommodityNum(3).setTicketNum(1);
        controller.buyGoods(purchase);
        assertEquals(Integer.valueOf(3), saved.getUserId());
        assertEquals(Integer.valueOf(OrderStatus.PENDING_DELIVERY), saved.getOrderStatus());
        assertEquals(Integer.valueOf(17), savedDetail.getAddressId());
        assertEquals(Integer.valueOf(3), savedDetail.getUserId());
        assertEquals(new BigDecimal("25.00"), saved.getOrderPrice());
    }

    @Test
    public void cannotPurchaseWithAnotherUsersAddressOrConsumeTheirTickets() {
        address.setUserId(99);
        try {
            controller.buyGoods(new OrderInfo().setCommodityId(5).setUserId(3)
                    .setAddressId(17).setCommodityNum(2).setTicketNum(1));
            fail("Expected address ownership validation");
        } catch (IllegalArgumentException expected) {
            assertNull(saved);
            assertNull(savedDetail);
            assertEquals(0, consumed);
        }
    }

    @Test
    public void cannotCompleteCheckoutWithoutAnAddress() {
        address = null;
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", 3);
        payload.put("lines", Collections.singletonList(Collections.singletonMap("orderId", 10)));
        try {
            controller.completeCart(payload);
            fail("Expected missing address validation");
        } catch (IllegalArgumentException expected) {
            assertNull(saved);
            assertEquals(0, consumed);
        }
    }

    @Test
    public void receiptOnlyCompletesOwnedPendingOrderAndSetsFinishTime() {
        cart.setOrderStatus(OrderStatus.PENDING_DELIVERY);
        controller.receipt(10, 3);
        assertNotNull(orderUpdate);
        assertTrue(orderUpdate.getSqlSet().contains("finish_date"));
        assertTrue(orderUpdate.getSqlSegment().contains("order_status"));
        assertTrue(orderUpdate.getSqlSegment().contains("user_id"));
    }

    @Test
    public void receiptRejectsAnotherUsersOrder() {
        cart.setOrderStatus(OrderStatus.PENDING_DELIVERY);
        try {
            controller.receipt(10, 99);
            fail("Expected order ownership validation");
        } catch (IllegalArgumentException expected) { assertNull(orderUpdate); }
    }

    @Test
    public void receiptRejectsUnpaidCancelledAndClosedOrders() {
        for (int status : new int[]{OrderStatus.UNPAID, OrderStatus.CANCELLED, OrderStatus.CLOSED}) {
            cart.setOrderStatus(status);
            try {
                controller.receipt(10, 3);
                fail("Expected receipt state validation");
            } catch (IllegalArgumentException expected) { assertNull(orderUpdate); }
        }
    }

    @Test
    public void repeatedReceiptDoesNotChangeCompletionTime() {
        cart.setOrderStatus(OrderStatus.COMPLETED);
        controller.receipt(10, 3);
        assertNull(orderUpdate);
    }

    private interface Invocation {
        Object call(String method, Object[] args);
    }

    private <T> T service(Class<T> type, Invocation invocation) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> invocation.call(method.getName(), args)));
    }
}
