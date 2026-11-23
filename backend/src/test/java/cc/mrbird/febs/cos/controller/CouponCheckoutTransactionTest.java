package cc.mrbird.febs.cos.controller;

import cc.mrbird.febs.cos.dao.*;
import cc.mrbird.febs.cos.entity.*;
import cc.mrbird.febs.cos.service.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Before;
import org.junit.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiFunction;

import static org.junit.Assert.*;

/** Exercise real SQL, mapper row locks and Spring transaction boundaries without touching production. */
public class CouponCheckoutTransactionTest {
    private JdbcTemplate jdbc;
    private WebController controller;
    private boolean failOrderSave;
    private int failOnSaveNumber;
    private int saveCount;

    @Before
    public void setUp() throws Exception {
        DriverManagerDataSource source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE coupon_template (id BIGINT PRIMARY KEY, name VARCHAR(100), coupon_type VARCHAR(20), discount_type VARCHAR(20), discount_value DECIMAL(10,2), min_spend DECIMAL(10,2), status INT)");
        jdbc.execute("CREATE TABLE coupon_commodity (id BIGINT PRIMARY KEY, coupon_id BIGINT, commodity_id INT)");
        jdbc.execute("CREATE TABLE user_coupon (id BIGINT PRIMARY KEY, coupon_id BIGINT, user_id INT, status INT, expire_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE order_info (id INT AUTO_INCREMENT PRIMARY KEY, code VARCHAR(100), user_id INT, commodity_id INT, order_status INT, order_price DECIMAL(10,2), user_coupon_id BIGINT, coupon_name VARCHAR(100), coupon_discount DECIMAL(10,2))");
        jdbc.execute("CREATE TABLE order_detail (code VARCHAR(100))");
        jdbc.execute("CREATE TABLE ticket_balance (user_id INT PRIMARY KEY, remaining INT)");
        jdbc.update("INSERT INTO coupon_template VALUES (10,'测试优惠券','ALL','AMOUNT',8,20,1)");
        jdbc.update("INSERT INTO user_coupon VALUES (100,10,7,0,DATEADD('DAY',1,CURRENT_TIMESTAMP))");
        jdbc.update("INSERT INTO ticket_balance VALUES (7,10)");
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(UserCouponMapper.class);
        configuration.addMapper(CouponTemplateMapper.class);
        configuration.addMapper(CouponCommodityMapper.class);
        configuration.addMapper(OrderInfoMapper.class);
        configuration.addMapper(OrderDetailMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source); factory.setConfiguration(configuration);
        SqlSessionFactory sqlFactory = factory.getObject();
        SqlSessionTemplate session = new SqlSessionTemplate(sqlFactory);
        DataSourceTransactionManager manager = new DataSourceTransactionManager(source);
        WaterTicketService water = new WaterTicketService(null, null, null, null, null) {
            @Override public int available(Integer user, Integer product) {
                return jdbc.queryForObject("SELECT remaining FROM ticket_balance WHERE user_id=?", Integer.class, user);
            }
            @Override public int consume(Integer user, Integer product, int amount, String code) {
                if (jdbc.update("UPDATE ticket_balance SET remaining=remaining-? WHERE user_id=? AND remaining>=?", amount, user, amount) != 1)
                    throw new IllegalArgumentException("水票不足");
                return amount;
            }
        };
        ICommodityInfoService products = stub(ICommodityInfoService.class, (method, args) -> new CommodityInfo()
                .setId((Integer) args[0]).setPrice(new BigDecimal("12.50")).setOnPut("1").setStockNum(100));
        CouponCheckoutService coupons = transactional(new CouponCheckoutService(session.getMapper(CouponTemplateMapper.class),
                session.getMapper(CouponCommodityMapper.class), session.getMapper(UserCouponMapper.class), products, water), manager);
        Map<Class<?>, Object> dependencies = new HashMap<>();
        dependencies.put(CouponCheckoutService.class, coupons);
        dependencies.put(WaterTicketService.class, water);
        dependencies.put(OrderInfoMapper.class, session.getMapper(OrderInfoMapper.class));
        dependencies.put(IAddressInfoService.class, stub(IAddressInfoService.class, (method, args) -> new AddressInfo().setId(17).setUserId(7)));
        dependencies.put(IOrderDetailService.class, stub(IOrderDetailService.class, (method, args) -> {
            if (method.equals("save")) jdbc.update("INSERT INTO order_detail VALUES (?)", ((OrderDetail) args[0]).getCode());
            return true;
        }));
        dependencies.put(IOrderInfoService.class, stub(IOrderInfoService.class, (method, args) -> {
            if (failOrderSave || ++saveCount == failOnSaveNumber) return false;
            OrderInfo order = (OrderInfo) args[0];
            if (method.equals("save")) {
                jdbc.update("INSERT INTO order_info(code,user_id,commodity_id,order_status,order_price,user_coupon_id,coupon_name,coupon_discount) VALUES (?,?,?,?,?,?,?,?)",
                        order.getCode(), order.getUserId(), order.getCommodityId(), order.getOrderStatus(), order.getOrderPrice(), order.getUserCouponId(), order.getCouponName(), order.getCouponDiscount());
            } else {
                jdbc.update("UPDATE order_info SET order_status=?,order_price=?,user_coupon_id=?,coupon_name=?,coupon_discount=? WHERE id=?",
                        order.getOrderStatus(), order.getOrderPrice(), order.getUserCouponId(), order.getCouponName(), order.getCouponDiscount(), order.getId());
            }
            return true;
        }));
        Constructor<?> constructor = WebController.class.getConstructors()[0];
        controller = transactional((WebController) constructor.newInstance(Arrays.stream(constructor.getParameterTypes()).map(dependencies::get).toArray()), manager);
    }

    @Test
    public void successfulPurchasePersistsCouponAndRejectsItsReuse() {
        controller.buyGoods(purchase());
        assertEquals(Integer.valueOf(1), jdbc.queryForObject("SELECT status FROM user_coupon", Integer.class));
        assertEquals(new BigDecimal("17.00"), jdbc.queryForObject("SELECT order_price FROM order_info", BigDecimal.class));
        assertEquals(Long.valueOf(100), jdbc.queryForObject("SELECT user_coupon_id FROM order_info", Long.class));
        try { controller.buyGoods(purchase()); fail("Coupon must not be reusable"); }
        catch (IllegalArgumentException expected) { assertEquals(Integer.valueOf(1), jdbc.queryForObject("SELECT COUNT(*) FROM order_info", Integer.class)); }
        assertEquals(Integer.valueOf(9), jdbc.queryForObject("SELECT remaining FROM ticket_balance", Integer.class));
    }

    @Test
    public void failedOrderSaveRollsBackCouponTicketsAndOrderDetailTogether() {
        failOrderSave = true;
        try { controller.buyGoods(purchase()); fail("Expected failed save"); }
        catch (IllegalStateException expected) {
            assertEquals(Integer.valueOf(0), jdbc.queryForObject("SELECT status FROM user_coupon", Integer.class));
            assertEquals(Integer.valueOf(10), jdbc.queryForObject("SELECT remaining FROM ticket_balance", Integer.class));
            assertEquals(Integer.valueOf(0), jdbc.queryForObject("SELECT COUNT(*) FROM order_info", Integer.class));
            assertEquals(Integer.valueOf(0), jdbc.queryForObject("SELECT COUNT(*) FROM order_detail", Integer.class));
        }
    }

    @Test
    public void concurrentPurchasesCanOnlyRedeemCouponOnce() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> submit = () -> {
            start.await();
            try { controller.buyGoods(purchase()); return true; }
            catch (IllegalArgumentException alreadyUsed) { return false; }
        };
        try {
            Future<Boolean> first = workers.submit(submit), second = workers.submit(submit);
            start.countDown();
            assertTrue(first.get(10, TimeUnit.SECONDS) ^ second.get(10, TimeUnit.SECONDS));
            assertEquals(Integer.valueOf(1), jdbc.queryForObject("SELECT COUNT(*) FROM order_info", Integer.class));
            assertEquals(Integer.valueOf(9), jdbc.queryForObject("SELECT remaining FROM ticket_balance", Integer.class));
        } finally { workers.shutdownNow(); }
    }

    @Test
    public void mixedCartOnlyDiscountsEligibleProductAndCannotBePaidTwice() {
        jdbc.update("UPDATE coupon_template SET coupon_type='PRODUCT',min_spend=0");
        jdbc.update("INSERT INTO coupon_commodity VALUES(1,10,2)");
        jdbc.update("INSERT INTO order_info(id,code,user_id,commodity_id,order_status) VALUES(1,'CART1',7,2,0),(2,'CART2',7,3,0)");
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", 7); payload.put("addressId", 17); payload.put("userCouponId", 100);
        List<Map<String, Object>> lines = new ArrayList<>();
        for (int id : new int[]{1,2}) {
            Map<String, Object> line = new HashMap<>();
            line.put("orderId", id); line.put("commodityNum", 2); line.put("ticketNum", 0); lines.add(line);
        }
        payload.put("lines", lines); controller.completeCart(payload);
        assertEquals(new BigDecimal("17.00"), jdbc.queryForObject("SELECT order_price FROM order_info WHERE id=1", BigDecimal.class));
        assertEquals(new BigDecimal("25.00"), jdbc.queryForObject("SELECT order_price FROM order_info WHERE id=2", BigDecimal.class));
        assertNull(jdbc.queryForObject("SELECT user_coupon_id FROM order_info WHERE id=2", Long.class));
        try { controller.completeCart(payload); fail("Cart must not be paid twice"); }
        catch (IllegalArgumentException expected) { assertEquals(Integer.valueOf(2), jdbc.queryForObject("SELECT COUNT(*) FROM order_info WHERE order_status=1", Integer.class)); }
    }

    @Test
    public void failureOnSecondCartOrderRollsBackFirstOrderAndCoupon() {
        jdbc.update("INSERT INTO order_info(id,code,user_id,commodity_id,order_status) VALUES(1,'CART1',7,2,0),(2,'CART2',7,3,0)");
        failOnSaveNumber = 2;
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", 7); payload.put("addressId", 17); payload.put("userCouponId", 100);
        List<Map<String, Object>> lines = new ArrayList<>();
        for (int id : new int[]{1, 2}) {
            Map<String, Object> line = new HashMap<>();
            line.put("orderId", id); line.put("commodityNum", 3); line.put("ticketNum", 1); lines.add(line);
        }
        payload.put("lines", lines);
        try { controller.completeCart(payload); fail("Expected partial cart save failure"); }
        catch (IllegalStateException expected) {
            assertEquals(Integer.valueOf(2), jdbc.queryForObject("SELECT COUNT(*) FROM order_info WHERE order_status=0", Integer.class));
            assertEquals(Integer.valueOf(0), jdbc.queryForObject("SELECT status FROM user_coupon", Integer.class));
            assertEquals(Integer.valueOf(10), jdbc.queryForObject("SELECT remaining FROM ticket_balance", Integer.class));
            assertEquals(Integer.valueOf(0), jdbc.queryForObject("SELECT COUNT(*) FROM order_info WHERE user_coupon_id IS NOT NULL", Integer.class));
        }
    }

    private OrderInfo purchase() {
        return new OrderInfo().setCommodityId(2).setUserId(7).setAddressId(17).setCommodityNum(3).setTicketNum(1).setUserCouponId(100L);
    }
    @SuppressWarnings("unchecked")
    private <T> T transactional(T target, DataSourceTransactionManager manager) {
        ProxyFactory proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return (T) proxy.getProxy();
    }
    private <T> T stub(Class<T> type, BiFunction<String, Object[], Object> invocation) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> invocation.apply(method.getName(), args)));
    }
}
