package cc.mrbird.febs.cos.service;

import cc.mrbird.febs.cos.dao.CouponCommodityMapper;
import cc.mrbird.febs.cos.dao.CouponTemplateMapper;
import cc.mrbird.febs.cos.dao.UserCouponMapper;
import cc.mrbird.febs.cos.entity.CouponCommodity;
import cc.mrbird.febs.cos.entity.CouponTemplate;
import cc.mrbird.febs.cos.entity.UserCoupon;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
        List<CouponTemplate> templates = couponMapper.selectList(Wrappers.<CouponTemplate>lambdaQuery()
                .eq(CouponTemplate::getStatus, 1).orderByDesc(CouponTemplate::getCreateTime));
        List<UserCoupon> userCoupons = userId == null ? Collections.emptyList() : userCouponMapper.selectList(
                Wrappers.<UserCoupon>lambdaQuery().eq(UserCoupon::getUserId, userId));
        Map<Long, UserCoupon> owned = userCoupons.stream().collect(Collectors.toMap(UserCoupon::getCouponId, c -> c, (a, b) -> a));
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
            row.put("available", inWindow && claim == null && (template.getTotalCount() == null ||
                    (template.getClaimedCount() != null && template.getClaimedCount() < template.getTotalCount())));
            result.add(row);
        }
        return result;
    }

    public List<CouponTemplate> listForAdmin() {
        List<CouponTemplate> templates = couponMapper.selectList(Wrappers.<CouponTemplate>lambdaQuery().orderByDesc(CouponTemplate::getCreateTime));
        for (CouponTemplate template : templates) {
            List<CouponCommodity> relations = commodityMapper.selectList(Wrappers.<CouponCommodity>lambdaQuery().eq(CouponCommodity::getCouponId, template.getId()));
            template.setCommodityIds(relations.stream().map(CouponCommodity::getCommodityId).collect(Collectors.toList()));
            template.setCommodityNames(relations.stream().map(r -> commodityService.getById(r.getCommodityId()))
                    .filter(Objects::nonNull).map(c -> c.getName()).collect(Collectors.joining("、")));
        }
        return templates;
    }

    @Transactional(rollbackFor = Exception.class)
    public CouponTemplate save(CouponTemplate template) {
        validate(template);
        if (template.getClaimedCount() == null) template.setClaimedCount(0);
        if (template.getStatus() == null) template.setStatus(1);
        if (template.getMinSpend() == null) template.setMinSpend(BigDecimal.ZERO);
        if (template.getTotalCount() != null && template.getTotalCount() <= 0) throw new IllegalArgumentException("发放数量必须大于0");
        if (template.getId() == null) couponMapper.insert(template);
        else {
            CouponTemplate existing = couponMapper.selectById(template.getId());
            if (existing == null) throw new IllegalArgumentException("优惠券不存在");
            template.setClaimedCount(existing.getClaimedCount());
            if (template.getTotalCount() != null && template.getTotalCount() < existing.getClaimedCount())
                throw new IllegalArgumentException("可领取数量不能低于已领取数量");
            couponMapper.updateById(template);
            commodityMapper.delete(Wrappers.<CouponCommodity>lambdaQuery().eq(CouponCommodity::getCouponId, template.getId()));
        }
        if ("PRODUCT".equals(template.getCouponType())) {
            if (template.getCommodityIds() == null || template.getCommodityIds().isEmpty()) throw new IllegalArgumentException("商品专用券至少选择一个商品");
            for (Integer id : new HashSet<>(template.getCommodityIds())) {
                if (commodityService.getById(id) == null) throw new IllegalArgumentException("优惠券关联商品不存在");
                CouponCommodity relation = new CouponCommodity(); relation.setCouponId(template.getId()); relation.setCommodityId(id); commodityMapper.insert(relation);
            }
        } else {
            commodityMapper.delete(Wrappers.<CouponCommodity>lambdaQuery().eq(CouponCommodity::getCouponId, template.getId()));
        }
        return template;
    }

    private void validate(CouponTemplate template) {
        if (template.getName() == null || template.getName().trim().isEmpty() ||
                !("ALL".equals(template.getCouponType()) || "PRODUCT".equals(template.getCouponType())) ||
                !("AMOUNT".equals(template.getDiscountType()) || "RATE".equals(template.getDiscountType())) ||
                template.getDiscountValue() == null || template.getDiscountValue().compareTo(BigDecimal.ZERO) <= 0 ||
                ("RATE".equals(template.getDiscountType()) && template.getDiscountValue().compareTo(BigDecimal.ONE) >= 0) ||
                template.getValidDays() == null || template.getValidDays() <= 0 ||
                (template.getMinSpend() != null && template.getMinSpend().compareTo(BigDecimal.ZERO) < 0)) {
            throw new IllegalArgumentException("请填写有效的优惠券名称、类型、优惠金额和有效期");
        }
        if (template.getStartTime() != null && template.getEndTime() != null && template.getEndTime().before(template.getStartTime()))
            throw new IllegalArgumentException("活动结束时间不能早于开始时间");
    }

    @Transactional(rollbackFor = Exception.class)
    public UserCoupon claim(Integer userId, Long couponId) {
        if (userId == null) throw new IllegalArgumentException("请先登录");
        CouponTemplate template = couponMapper.selectById(couponId);
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

    public void disable(Long id) {
        CouponTemplate template = couponMapper.selectById(id);
        if (template == null) throw new IllegalArgumentException("优惠券不存在");
        template.setStatus(0); couponMapper.updateById(template);
    }
}
