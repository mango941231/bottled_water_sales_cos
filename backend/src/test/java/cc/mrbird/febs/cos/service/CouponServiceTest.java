package cc.mrbird.febs.cos.service;

import cc.mrbird.febs.cos.dao.CouponCommodityMapper;
import cc.mrbird.febs.cos.dao.CouponTemplateMapper;
import cc.mrbird.febs.cos.dao.UserCouponMapper;
import cc.mrbird.febs.cos.entity.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class CouponServiceTest {
    private CouponService service;
    private CouponTemplate existing;
    private CouponTemplate inserted;
    private final List<CouponCommodity> relations = new ArrayList<>();
    private final Map<Integer, CommodityInfo> products = new HashMap<>();
    private AbstractWrapper<?, ?, ?> update;
    private Wrapper<?> query;
    private int relationDeletes;
    private int claimedWrites;

    @Before
    public void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "coupon-test");
        for (Class<?> entity : Arrays.asList(CouponTemplate.class, CouponCommodity.class, UserCoupon.class))
            TableInfoHelper.initTableInfo(assistant, entity);
        products.put(2, new CommodityInfo().setId(2).setName("纯净水").setDelFlag("0"));
        products.put(3, new CommodityInfo().setId(3).setName("矿泉水").setDelFlag("0"));
        CouponTemplateMapper templates = stub(CouponTemplateMapper.class, (method, args) -> {
            switch (method) {
                case "insert": inserted = (CouponTemplate) args[0]; inserted.setId(10L); return 1;
                case "selectById": case "selectForUpdate": return existing;
                case "update": update = (AbstractWrapper<?, ?, ?>) args[1]; return 1;
                case "selectList": query = (Wrapper<?>) args[0]; return existing == null ? Collections.emptyList() : Collections.singletonList(existing);
                case "selectPage": query = (Wrapper<?>) args[1]; return args[0];
                default: throw new AssertionError(method);
            }
        });
        CouponCommodityMapper relationMapper = stub(CouponCommodityMapper.class, (method, args) -> {
            switch (method) {
                case "delete": relationDeletes++; relations.clear(); return 1;
                case "insert": relations.add((CouponCommodity) args[0]); return 1;
                case "selectList": return new ArrayList<>(relations);
                default: throw new AssertionError(method);
            }
        });
        UserCouponMapper claims = stub(UserCouponMapper.class, (method, args) -> {
            if (method.equals("selectList")) {
                UserCoupon claim = new UserCoupon(); claim.setCouponId(10L); claim.setStatus(0);
                return Collections.singletonList(claim);
            }
            claimedWrites++;
            throw new AssertionError("Issued coupon records must not be changed by admin CRUD");
        });
        service = new CouponService(templates, relationMapper, claims,
                stub(ICommodityInfoService.class, (method, args) -> products.get(args[0])));
    }

    @Test
    public void productCouponSupportsMultipleProductsWithoutDuplicateRelations() {
        CouponTemplate template = valid(); template.setCouponType("PRODUCT");
        template.setCommodityIds(Arrays.asList(2, 3, 2));
        template.setClaimedCount(900);
        service.save(template);
        assertSame(template, inserted);
        assertEquals(Arrays.asList(2, 3), template.getCommodityIds());
        assertEquals(2, relations.size());
        assertEquals(Long.valueOf(10), relations.get(1).getCouponId());
        assertEquals(Integer.valueOf(0), inserted.getClaimedCount());
        assertNotNull(inserted.getCreateTime());
    }

    @Test
    public void productCouponRequiresExistingNonDeletedProductsBeforeWriting() {
        CouponTemplate template = valid(); template.setCouponType("PRODUCT");
        expectInvalid(() -> service.save(template));
        template.setCommodityIds(Arrays.asList(2, 999));
        expectInvalid(() -> service.save(template));
        products.get(3).setDelFlag("1"); template.setCommodityIds(Collections.singletonList(3));
        expectInvalid(() -> service.save(template));
        assertNull(inserted);
        assertEquals(0, relationDeletes);
    }

    @Test
    public void editCanSwitchToAllProductsAndClearQuotaAndDatesWithoutResettingClaims() {
        existing = valid(); existing.setId(10L); existing.setClaimedCount(7);
        existing.setTotalCount(20); existing.setStartTime(new Date()); existing.setEndTime(new Date());
        CouponCommodity relation = new CouponCommodity(); relation.setCommodityId(2); relation.setCouponId(10L); relations.add(relation);
        CouponTemplate edited = valid(); edited.setId(10L); edited.setCommodityIds(Arrays.asList(2, 3));
        service.save(edited);
        assertEquals(Integer.valueOf(7), edited.getClaimedCount());
        assertEquals(Collections.emptyList(), edited.getCommodityIds());
        assertTrue(relations.isEmpty());
        assertNull(updatedValue("total_count"));
        assertNull(updatedValue("start_time"));
        assertNull(updatedValue("end_time"));
        assertFalse(update.getSqlSet().contains("claimed_count"));
        assertEquals(0, claimedWrites);
    }

    @Test
    public void quotaCannotBeReducedBelowAlreadyClaimedCount() {
        existing = valid(); existing.setId(10L); existing.setClaimedCount(7);
        CouponTemplate edited = valid(); edited.setId(10L); edited.setTotalCount(6);
        expectInvalid(() -> service.save(edited));
        assertNull(update);
        assertEquals(0, relationDeletes);
    }

    @Test
    public void deleteHidesTemplateAndPreservesIssuedCouponsAndProducts() {
        existing = valid(); existing.setId(10L);
        CouponCommodity relation = new CouponCommodity(); relation.setCommodityId(2); relations.add(relation);
        service.delete(10L);
        assertEquals(-1, updatedValue("status"));
        assertEquals(1, relations.size());
        assertEquals(0, relationDeletes);
        assertEquals(0, claimedWrites);
        existing.setStatus(-1);
        expectInvalid(() -> service.detailForAdmin(10L));
        CouponTemplate edited = valid(); edited.setId(10L);
        expectInvalid(() -> service.save(edited));
        expectInvalid(() -> service.claim(3, 10L));
    }

    @Test
    public void claimedTabStillShowsDeletedCouponHistory() {
        existing = valid(); existing.setId(10L); existing.setStatus(-1);
        List<Map<String, Object>> rows = service.listForApp(3, "claimed");
        assertEquals(1, rows.size());
        assertEquals(true, rows.get(0).get("claimed"));
        assertEquals(false, rows.get(0).get("available"));
        assertTrue(query.getSqlSegment().contains("id IN"));
    }

    @Test
    public void adminQueryIncludesScopeStatusNameAndExcludesDeletedRecords() {
        service.pageForAdmin(1, 10, "纯净水", "PRODUCT", 0);
        String sql = query.getSqlSegment();
        assertTrue(sql.contains("status <>"));
        assertTrue(sql.contains("name LIKE"));
        assertTrue(sql.contains("coupon_type ="));
        assertTrue(sql.contains("status ="));
        Map<String, Object> params = ((AbstractWrapper<?, ?, ?>) query).getParamNameValuePairs();
        assertTrue(params.containsValue(-1));
        assertTrue(params.containsValue(0));
        assertTrue(params.containsValue("PRODUCT"));
        assertTrue(params.containsValue("%纯净水%"));
        expectInvalid(() -> service.pageForAdmin(1, 101, null, null, null));
        expectInvalid(() -> service.pageForAdmin(1, 10, null, null, -1));
    }

    @Test
    public void detailsIncludeEverySelectedProduct() {
        existing = valid(); existing.setId(10L); existing.setCouponType("PRODUCT");
        for (int id : Arrays.asList(2, 3)) {
            CouponCommodity relation = new CouponCommodity(); relation.setCouponId(10L); relation.setCommodityId(id); relations.add(relation);
        }
        CouponTemplate detail = service.detailForAdmin(10L);
        assertEquals(Arrays.asList(2, 3), detail.getCommodityIds());
        assertEquals("纯净水、矿泉水", detail.getCommodityNames());
    }

    @Test
    public void invalidDiscountStatusAndDateWindowAreRejected() {
        CouponTemplate template = valid(); template.setDiscountType("RATE"); template.setDiscountValue(BigDecimal.ONE);
        expectInvalid(() -> service.save(template));
        template.setDiscountValue(new BigDecimal("0.90")); template.setStatus(-1);
        expectInvalid(() -> service.save(template));
        template.setStatus(1); template.setStartTime(new Date(2000)); template.setEndTime(new Date(1000));
        expectInvalid(() -> service.save(template));
        assertNull(inserted);
    }

    private Object updatedValue(String column) {
        Matcher matcher = Pattern.compile(column + "=\\#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)\\}").matcher(update.getSqlSet());
        assertTrue("Missing explicit update for " + column, matcher.find());
        return update.getParamNameValuePairs().get(matcher.group(1));
    }

    private CouponTemplate valid() {
        CouponTemplate template = new CouponTemplate(); template.setName("优惠券"); template.setCouponType("ALL");
        template.setDiscountType("AMOUNT"); template.setDiscountValue(new BigDecimal("5.00"));
        template.setMinSpend(BigDecimal.ZERO); template.setValidDays(30); template.setStatus(1); template.setClaimedCount(0);
        return template;
    }

    private void expectInvalid(Runnable action) {
        try { action.run(); fail("Expected validation error"); } catch (IllegalArgumentException expected) { }
    }

    @SuppressWarnings("unchecked")
    private <T> T stub(Class<T> type, BiFunction<String, Object[], Object> handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> handler.apply(method.getName(), args));
    }
}
