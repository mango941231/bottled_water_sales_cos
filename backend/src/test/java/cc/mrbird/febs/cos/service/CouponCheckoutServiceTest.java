package cc.mrbird.febs.cos.service;

import cc.mrbird.febs.cos.dao.*;
import cc.mrbird.febs.cos.entity.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.toolkit.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.BiFunction;

import static org.junit.Assert.*;

public class CouponCheckoutServiceTest {
    private CouponCheckoutService service;
    private CouponTemplate template;
    private UserCoupon coupon;
    private final Map<Integer, CommodityInfo> products = new HashMap<>();
    private final List<CouponCommodity> relations = new ArrayList<>();
    private final List<UserCoupon> owned = new ArrayList<>();
    private int redeemed;
    private int redeemResult = 1;
    private int ticketBalance = 10;

    @Before
    public void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "coupon-checkout-test");
        for (Class<?> entity : Arrays.asList(CouponTemplate.class, CouponCommodity.class, UserCoupon.class))
            TableInfoHelper.initTableInfo(assistant, entity);
        products.put(2, product(2, "12.50"));
        products.put(3, product(3, "20.00"));
        template = new CouponTemplate();
        template.setId(10L); template.setName("满20减8"); template.setStatus(1);
        template.setCouponType("ALL"); template.setDiscountType("AMOUNT");
        template.setDiscountValue(new BigDecimal("8.00")); template.setMinSpend(new BigDecimal("20.00"));
        coupon = new UserCoupon(); coupon.setId(100L); coupon.setCouponId(10L); coupon.setUserId(7);
        coupon.setStatus(0); coupon.setExpireTime(new Date(System.currentTimeMillis() + 86400000));
        owned.add(coupon);
        service = new CouponCheckoutService(
            stub(CouponTemplateMapper.class, (method, args) -> template),
            stub(CouponCommodityMapper.class, (method, args) -> new ArrayList<>(relations)),
            stub(UserCouponMapper.class, (method, args) -> {
                if (method.equals("selectList")) return new ArrayList<>(owned);
                if (method.equals("selectForUpdate")) return Objects.equals(args[0], coupon.getId()) ? coupon : null;
                if (method.equals("redeem")) { redeemed++; return redeemResult; }
                throw new AssertionError(method);
            }),
            stub(ICommodityInfoService.class, (method, args) -> products.get(args[0])),
            new WaterTicketService(null, null, null, null, null) {
                @Override public int available(Integer userId, Integer commodityId) { return ticketBalance; }
            });
    }

    @Test
    public void allCouponDiscountsMixedCartAndStoresExactOrderShares() {
        List<OrderInfo> lines = Arrays.asList(line(2, 2, 0), line(3, 1, 0));
        CouponCheckoutService.Quote quote = service.settle(7, lines, 100L);
        assertMoney("45.00", quote.getTotalPrice()); assertMoney("8.00", quote.getCouponDiscount());
        assertMoney("37.00", quote.getPayable());
        assertMoney("8.00", lines.stream().map(OrderInfo::getCouponDiscount).reduce(BigDecimal.ZERO, BigDecimal::add));
        for (OrderInfo line : lines) {
            assertEquals(Long.valueOf(100), line.getUserCouponId());
            assertEquals(template.getName(), line.getCouponName());
        }
        assertEquals(1, redeemed);
    }

    @Test
    public void productCouponOnlyDiscountsConfiguredProductsAndSupportsMultipleRelations() {
        scope(2, 3);
        List<OrderInfo> lines = Arrays.asList(line(2, 1, 0), line(3, 1, 0));
        assertMoney("24.50", service.settle(7, lines, 100L).getPayable());
        relations.remove(1); template.setMinSpend(BigDecimal.ZERO);
        lines = Arrays.asList(line(2, 1, 0), line(3, 1, 0));
        assertMoney("24.50", service.settle(7, lines, 100L).getPayable());
        assertMoney("4.50", lines.get(0).getOrderPrice());
        assertMoney("20.00", lines.get(1).getOrderPrice());
        assertNull(lines.get(1).getUserCouponId()); assertMoney("0.00", lines.get(1).getCouponDiscount());
    }

    @Test
    public void unrelatedProductsDoNotHelpMeetMinimumSpend() {
        scope(2);
        expectInvalid(() -> service.settle(7, Arrays.asList(line(2, 1, 0), line(3, 10, 0)), 100L), "门槛");
        assertEquals(0, redeemed);
    }

    @Test
    public void waterTicketsDeductBeforeCheckingMinimumAndCouponAmount() {
        List<OrderInfo> lines = Collections.singletonList(line(2, 3, 1));
        CouponCheckoutService.Quote quote = service.settle(7, lines, 100L);
        assertMoney("37.50", quote.getTotalPrice()); assertMoney("12.50", quote.getTicketDiscount());
        assertMoney("17.00", quote.getPayable());
        expectInvalid(() -> service.settle(7, Collections.singletonList(line(2, 3, 2)), 100L), "门槛");
        expectInvalid(() -> service.settle(7, Collections.singletonList(line(2, 3, 3)), 100L), "没有可抵扣");
    }

    @Test
    public void oversizedAmountCouponNeverMakesOrdersNegative() {
        template.setDiscountValue(new BigDecimal("999.00"));
        List<OrderInfo> lines = Arrays.asList(line(2, 1, 0), line(3, 1, 0));
        assertMoney("0.00", service.settle(7, lines, 100L).getPayable());
        for (OrderInfo line : lines) assertMoney("0.00", line.getOrderPrice());
    }

    @Test
    public void rateDiscountUsesCentsAndAllocatesOddCentsExactly() {
        products.get(2).setPrice(new BigDecimal("0.05")); template.setMinSpend(BigDecimal.ZERO);
        template.setDiscountType("RATE"); template.setDiscountValue(new BigDecimal("0.50"));
        List<OrderInfo> lines = Arrays.asList(line(2, 1, 0), line(2, 1, 0), line(2, 1, 0));
        CouponCheckoutService.Quote quote = service.settle(7, lines, 100L);
        assertMoney("0.08", quote.getPayable()); assertMoney("0.07", quote.getCouponDiscount());
        assertMoney("0.07", lines.stream().map(OrderInfo::getCouponDiscount).reduce(BigDecimal.ZERO, BigDecimal::add));
        for (OrderInfo line : lines) assertTrue(line.getOrderPrice().signum() >= 0);
    }

    @Test
    public void selectingOneCouponDoesNotChangeOtherChoicesOrRedeemOnPreview() {
        UserCoupon other = new UserCoupon(); other.setId(101L); other.setCouponId(10L); other.setUserId(7);
        other.setStatus(0); other.setExpireTime(coupon.getExpireTime()); owned.add(other);
        CouponCheckoutService.Quote quote = service.preview(7, Collections.singletonList(line(2, 2, 0)), 100L);
        assertEquals(2, quote.getCoupons().size());
        for (Map<String, Object> choice : quote.getCoupons()) {
            assertEquals(true, choice.get("available")); assertMoney("8.00", (BigDecimal) choice.get("discount"));
        }
        assertMoney("17.00", quote.getPayable()); assertEquals(0, redeemed);
    }

    @Test
    public void invalidSelectionIsClearedWithReasonWhilePreviewRemainsUsable() {
        scope(3);
        CouponCheckoutService.Quote quote = service.preview(7, Collections.singletonList(line(2, 2, 0)), 100L);
        assertNull(quote.getSelectedUserCouponId()); assertNotNull(quote.getSelectedCouponError());
        assertEquals(false, quote.getCoupons().get(0).get("available"));
        assertMoney("25.00", quote.getPayable()); assertEquals(0, redeemed);
        assertNotNull(service.preview(7, Collections.singletonList(line(2, 2, 0)), 999L).getSelectedCouponError());
    }

    @Test
    public void rejectsOtherUsersUsedExpiredDisabledAndOutOfDateCoupons() {
        Runnable submit = () -> service.settle(7, Collections.singletonList(line(2, 2, 0)), 100L);
        coupon.setUserId(8); expectInvalid(submit, "不属于"); coupon.setUserId(7);
        coupon.setStatus(1); expectInvalid(submit, "已使用"); coupon.setStatus(0);
        Date future = coupon.getExpireTime(); coupon.setExpireTime(new Date(0)); expectInvalid(submit, "过期"); coupon.setExpireTime(future);
        template.setStatus(0); expectInvalid(submit, "停用"); template.setStatus(-1); expectInvalid(submit, "删除"); template.setStatus(1);
        template.setStartTime(future); expectInvalid(submit, "尚未生效"); template.setStartTime(null);
        template.setEndTime(new Date(0)); expectInvalid(submit, "结束");
        assertEquals(0, redeemed);
    }

    @Test
    public void atomicRedemptionFailureCannotReturnSuccessfulSettlement() {
        redeemResult = 0;
        expectInvalid(() -> service.settle(7, Collections.singletonList(line(2, 2, 0)), 100L), "已使用或已过期");
    }

    @Test
    public void aggregatesStockAndWaterTicketsAcrossSameProductLines() {
        products.get(2).setStockNum(3);
        expectInvalid(() -> service.settle(7, Arrays.asList(line(2, 2, 0), line(2, 2, 0)), null), "库存");
        products.get(2).setStockNum(100); ticketBalance = 2;
        expectInvalid(() -> service.settle(7, Arrays.asList(line(2, 2, 2), line(2, 1, 1)), null), "水票余额");
    }

    @Test
    public void forgedPricesDiscountsAndCouponMetadataAreIgnored() {
        OrderInfo line = line(2, 2, 0).setOrderPrice(new BigDecimal("0.01"))
                .setCouponDiscount(new BigDecimal("999" )).setCouponName("伪造").setUserCouponId(999L);
        assertMoney("25.00", service.settle(7, Collections.singletonList(line), null).getPayable());
        assertMoney("25.00", line.getOrderPrice()); assertMoney("0.00", line.getCouponDiscount());
        assertNull(line.getCouponName()); assertNull(line.getUserCouponId()); assertEquals(0, redeemed);
    }

    @Test
    public void rejectsDeletedGoodsInvalidQuantitiesAndDiscountsRoundingToZero() {
        products.get(2).setDelFlag("1");
        expectInvalid(() -> service.settle(7, Collections.singletonList(line(2, 1, 0)), null), "下架");
        products.get(2).setDelFlag("0");
        expectInvalid(() -> service.settle(7, Collections.singletonList(line(2, 0, 0)), null), "数量");
        expectInvalid(() -> service.settle(7, Collections.singletonList(line(2, 1, 2)), null), "数量");
        products.get(2).setPrice(new BigDecimal("0.01")); template.setMinSpend(BigDecimal.ZERO);
        template.setDiscountType("RATE"); template.setDiscountValue(new BigDecimal("0.99"));
        expectInvalid(() -> service.settle(7, Collections.singletonList(line(2, 1, 0)), 100L), "不足0.01");
        assertEquals(0, redeemed);
    }

    private void scope(Integer... ids) {
        template.setCouponType("PRODUCT"); relations.clear();
        for (Integer id : ids) {
            CouponCommodity relation = new CouponCommodity(); relation.setCouponId(10L); relation.setCommodityId(id); relations.add(relation);
        }
    }
    private CommodityInfo product(int id, String price) {
        return new CommodityInfo().setId(id).setOnPut("1").setDelFlag("0").setPrice(new BigDecimal(price)).setStockNum(100);
    }
    private OrderInfo line(int id, int quantity, int tickets) {
        return new OrderInfo().setCommodityId(id).setCommodityNum(quantity).setTicketNum(tickets);
    }
    private void assertMoney(String expected, BigDecimal actual) { assertEquals(new BigDecimal(expected), actual); }
    private void expectInvalid(Runnable action, String reason) {
        try { action.run(); fail("Expected rejection: " + reason); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage(), expected.getMessage().contains(reason)); }
    }
    private <T> T stub(Class<T> type, BiFunction<String, Object[], Object> invocation) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> invocation.apply(method.getName(), args)));
    }
}
