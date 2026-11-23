package cc.mrbird.febs.cos.service;

import cc.mrbird.febs.cos.dao.CouponCommodityMapper;
import cc.mrbird.febs.cos.dao.CouponTemplateMapper;
import cc.mrbird.febs.cos.dao.UserCouponMapper;
import cc.mrbird.febs.cos.entity.CouponCommodity;
import cc.mrbird.febs.cos.entity.CouponTemplate;
import cc.mrbird.febs.cos.entity.CommodityInfo;
import cc.mrbird.febs.cos.entity.UserCoupon;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CouponService {
    private final CouponTemplateMapper couponMapper;
    private final CouponCommodityMapper commodityMapper;
    private final UserCouponMapper userCouponMapper;
    private final ICommodityInfoService commodityService;

    public List<Map<String, Object>> listForApp(Integer userId, String filter) {
        Date now = new Date();
        List<UserCoupon> userCoupons = userId == null ? Collections.emptyList() : userCouponMapper.selectList(
                Wrappers.<UserCoupon>lambdaQuery().eq(UserCoupon::getUserId, userId));
        Map<Long, UserCoupon> owned = userCoupons.stream().collect(Collectors.toMap(UserCoupon::getCouponId, c -> c, (a, b) -> a));
        if ("claimed".equals(filter) && owned.isEmpty()) return Collections.emptyList();
        List<CouponTemplate> templates = couponMapper.selectList(Wrappers.<CouponTemplate>lambdaQuery()
                .in("claimed".equals(filter), CouponTemplate::getId, owned.keySet())
                .eq(!"claimed".equals(filter), CouponTemplate::getStatus, 1).orderByDesc(CouponTemplate::getCreateTime));
        List<Map<String, Object>> result = new ArrayList<>();
        for (CouponTemplate template : templates) {
            boolean inWindow = (template.getStartTime() == null || !now.before(template.getStartTime())) &&
                    (template.getEndTime() == null || !now.after(template.getEndTime()));
            UserCoupon claim = owned.get(template.getId());
            if ("available".equals(filter) && (!inWindow || claim != null)) continue;
            if ("claimed".equals(filter) && claim == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", template.getId()); row.put("name", template.getName());
            row.put("couponType", template.getCouponType()); row.put("discountType", template.getDiscountType());
            row.put("discountValue", template.getDiscountValue()); row.put("minSpend", template.getMinSpend());
            row.put("validDays", template.getValidDays()); row.put("startTime", template.getStartTime());
            row.put("endTime", template.getEndTime()); row.put("claimed", claim != null);
            row.put("userCouponStatus", claim == null ? null : claim.getStatus());
            row.put("userCouponExpireTime", claim == null ? null : claim.getExpireTime());
            List<CouponCommodity> relations = commodityMapper.selectList(Wrappers.<CouponCommodity>lambdaQuery().eq(CouponCommodity::getCouponId, template.getId()));
            List<String> names = new ArrayList<>();
            for (CouponCommodity relation : relations) {
                cc.mrbird.febs.cos.entity.CommodityInfo commodity = commodityService.getById(relation.getCommodityId());
                if (commodity != null) names.add(commodity.getName());
            }
            row.put("commodityNames", names);
            row.put("available", Integer.valueOf(1).equals(template.getStatus()) && inWindow && claim == null && (template.getTotalCount() == null ||
                    (template.getClaimedCount() != null && template.getClaimedCount() < template.getTotalCount())));
            result.add(row);
        }
        return result;
    }

    public List<CouponTemplate> listForAdmin() {
        List<CouponTemplate> templates = couponMapper.selectList(adminQuery(null, null, null));
        templates.forEach(this::fillCommodities);
        return templates;
    }

    public IPage<CouponTemplate> pageForAdmin(long current, long size, String name, String couponType, Integer status) {
        if (current < 1 || size < 1 || size > 100) throw new IllegalArgumentException("分页参数无效，每页最多100条");
        if (couponType != null && !couponType.isEmpty() && !Arrays.asList("ALL", "PRODUCT").contains(couponType))
            throw new IllegalArgumentException("优惠券类型无效");
        if (status != null && status != 0 && status != 1) throw new IllegalArgumentException("优惠券状态无效");
        IPage<CouponTemplate> page = couponMapper.selectPage(new Page<>(current, size), adminQuery(name, couponType, status));
        page.getRecords().forEach(this::fillCommodities);
        return page;
    }

    private LambdaQueryWrapper<CouponTemplate> adminQuery(String name, String couponType, Integer status) {
        return Wrappers.<CouponTemplate>lambdaQuery().ne(CouponTemplate::getStatus, -1)
                .like(name != null && !name.trim().isEmpty(), CouponTemplate::getName, name == null ? null : name.trim())
                .eq(couponType != null && !couponType.isEmpty(), CouponTemplate::getCouponType, couponType)
                .eq(status != null, CouponTemplate::getStatus, status)
                .orderByDesc(CouponTemplate::getCreateTime).orderByDesc(CouponTemplate::getId);
    }

    public CouponTemplate detailForAdmin(Long id) {
        CouponTemplate template = requireExisting(couponMapper.selectById(id));
        fillCommodities(template);
        return template;
    }

    private CouponTemplate requireExisting(CouponTemplate template) {
        if (template == null || Integer.valueOf(-1).equals(template.getStatus())) throw new IllegalArgumentException("优惠券不存在或已删除");
        return template;
    }

    private void fillCommodities(CouponTemplate template) {
        List<CouponCommodity> relations = commodityMapper.selectList(Wrappers.<CouponCommodity>lambdaQuery().eq(CouponCommodity::getCouponId, template.getId()));
        template.setCommodityIds(relations.stream().map(CouponCommodity::getCommodityId).collect(Collectors.toList()));
        template.setCommodityNames(relations.stream().map(r -> {
            CommodityInfo commodity = commodityService.getById(r.getCommodityId());
            return commodity == null ? "商品已删除（ID:" + r.getCommodityId() + "）" : commodity.getName();
        }).collect(Collectors.joining("、")));
    }

    @Transactional(rollbackFor = Exception.class)
    public CouponTemplate save(CouponTemplate template) {
        validate(template);
        template.setName(template.getName().trim());
        if (template.getStatus() == null) template.setStatus(1);
        if (template.getMinSpend() == null) template.setMinSpend(BigDecimal.ZERO);
        if (template.getTotalCount() != null && template.getTotalCount() <= 0) throw new IllegalArgumentException("发放数量必须大于0");
        Set<Integer> productIds = new LinkedHashSet<>();
        if ("PRODUCT".equals(template.getCouponType())) {
            if (template.getCommodityIds() == null || template.getCommodityIds().isEmpty()) throw new IllegalArgumentException("商品专用券至少选择一个商品");
            for (Integer id : template.getCommodityIds()) {
                CommodityInfo commodity = id == null ? null : commodityService.getById(id);
                if (commodity == null || !"0".equals(commodity.getDelFlag())) throw new IllegalArgumentException("优惠券关联商品不存在或已删除");
                productIds.add(id);
            }
        }
        if (template.getId() == null) {
            template.setClaimedCount(0);
            template.setCreateTime(new Date());
            couponMapper.insert(template);
        }
        else {
            CouponTemplate existing = requireExisting(couponMapper.selectForUpdate(template.getId()));
            template.setClaimedCount(existing.getClaimedCount());
            template.setCreateTime(existing.getCreateTime());
            if (template.getTotalCount() != null && template.getTotalCount() < existing.getClaimedCount())
                throw new IllegalArgumentException("可领取数量不能低于已领取数量");
            // Explicit SETs allow clearing a previous quota or either activity date.
            couponMapper.update(null, Wrappers.<CouponTemplate>lambdaUpdate().eq(CouponTemplate::getId, template.getId())
                    .set(CouponTemplate::getName, template.getName()).set(CouponTemplate::getCouponType, template.getCouponType())
                    .set(CouponTemplate::getDiscountType, template.getDiscountType()).set(CouponTemplate::getDiscountValue, template.getDiscountValue())
                    .set(CouponTemplate::getMinSpend, template.getMinSpend()).set(CouponTemplate::getValidDays, template.getValidDays())
                    .set(CouponTemplate::getTotalCount, template.getTotalCount()).set(CouponTemplate::getStartTime, template.getStartTime())
                    .set(CouponTemplate::getEndTime, template.getEndTime()).set(CouponTemplate::getStatus, template.getStatus()));
        }
        commodityMapper.delete(Wrappers.<CouponCommodity>lambdaQuery().eq(CouponCommodity::getCouponId, template.getId()));
        for (Integer id : productIds) {
            CouponCommodity relation = new CouponCommodity(); relation.setCouponId(template.getId()); relation.setCommodityId(id); commodityMapper.insert(relation);
        }
        template.setCommodityIds(new ArrayList<>(productIds));
        return template;
    }

    private void validate(CouponTemplate template) {
        if (template.getName() == null || template.getName().trim().isEmpty() || template.getName().trim().length() > 100 ||
                !("ALL".equals(template.getCouponType()) || "PRODUCT".equals(template.getCouponType())) ||
                !("AMOUNT".equals(template.getDiscountType()) || "RATE".equals(template.getDiscountType())) ||
                template.getDiscountValue() == null || template.getDiscountValue().compareTo(BigDecimal.ZERO) <= 0 ||
                ("RATE".equals(template.getDiscountType()) && template.getDiscountValue().compareTo(BigDecimal.ONE) >= 0) ||
                template.getValidDays() == null || template.getValidDays() <= 0 ||
                (template.getMinSpend() != null && template.getMinSpend().compareTo(BigDecimal.ZERO) < 0)) {
            throw new IllegalArgumentException("请填写有效的优惠券名称、类型、优惠金额和有效期");
        }
        if ((template.getStatus() != null && template.getStatus() != 0 && template.getStatus() != 1) ||
                template.getDiscountValue().compareTo(new BigDecimal("99999999.99")) > 0 || template.getDiscountValue().stripTrailingZeros().scale() > 2 ||
                (template.getMinSpend() != null && (template.getMinSpend().compareTo(new BigDecimal("99999999.99")) > 0 || template.getMinSpend().stripTrailingZeros().scale() > 2)))
            throw new IllegalArgumentException("状态无效或金额超出范围，金额最多保留两位小数");
        if (template.getStartTime() != null && template.getEndTime() != null && template.getEndTime().before(template.getStartTime()))
            throw new IllegalArgumentException("活动结束时间不能早于开始时间");
    }

    @Transactional(rollbackFor = Exception.class)
    public UserCoupon claim(Integer userId, Long couponId) {
        if (userId == null) throw new IllegalArgumentException("请先登录");
        CouponTemplate template = couponMapper.selectForUpdate(couponId);
        Date now = new Date();
        if (template == null || template.getStatus() != 1 ||
                (template.getStartTime() != null && now.before(template.getStartTime())) ||
                (template.getEndTime() != null && now.after(template.getEndTime()))) throw new IllegalArgumentException("优惠券当前不可领取");
        if (userCouponMapper.selectCount(Wrappers.<UserCoupon>lambdaQuery().eq(UserCoupon::getUserId, userId).eq(UserCoupon::getCouponId, couponId)) > 0)
            throw new IllegalArgumentException("该优惠券已领取");
        if (template.getTotalCount() != null && (template.getClaimedCount() == null || template.getClaimedCount() >= template.getTotalCount())) throw new IllegalArgumentException("优惠券已领完");
        if (couponMapper.incrementClaimed(couponId) != 1) throw new IllegalArgumentException("优惠券已领完或已下架");
        Calendar expiry = Calendar.getInstance(); expiry.setTime(now); expiry.add(Calendar.DAY_OF_YEAR, template.getValidDays());
        if (template.getEndTime() != null && expiry.getTime().after(template.getEndTime())) expiry.setTime(template.getEndTime());
        UserCoupon claim = new UserCoupon(); claim.setUserId(userId); claim.setCouponId(couponId); claim.setStatus(0);
        claim.setReceiveTime(now); claim.setExpireTime(expiry.getTime()); userCouponMapper.insert(claim);
        return claim;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireExisting(couponMapper.selectForUpdate(id));
        // Keep issued coupons and their product relations for history.
        couponMapper.update(null, Wrappers.<CouponTemplate>lambdaUpdate().eq(CouponTemplate::getId, id).set(CouponTemplate::getStatus, -1));
    }
}
