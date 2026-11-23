package cc.mrbird.febs.cos.service;

import cc.mrbird.febs.cos.dao.CouponCommodityMapper;
import cc.mrbird.febs.cos.dao.CouponTemplateMapper;
import cc.mrbird.febs.cos.dao.UserCouponMapper;
import cc.mrbird.febs.cos.entity.*;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CouponCheckoutService {
    private final CouponTemplateMapper templateMapper;
    private final CouponCommodityMapper relationMapper;
    private final UserCouponMapper userCouponMapper;
    private final ICommodityInfoService commodityService;
    private final WaterTicketService waterTicketService;
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    @Data
    public static class Quote {
        private BigDecimal totalPrice = ZERO;
        private BigDecimal ticketDiscount = ZERO;
        private BigDecimal subtotal = ZERO;
        private BigDecimal couponDiscount = ZERO;
        private BigDecimal payable = ZERO;
        private Long selectedUserCouponId;
        private String couponName;
        private String selectedCouponError;
        private List<Map<String, Object>> coupons = new ArrayList<>();
    }

    public Quote preview(Integer userId, List<OrderInfo> lines, Long selectedId) {
        Quote quote = price(userId, lines);
        List<UserCoupon> owned = userCouponMapper.selectList(Wrappers.<UserCoupon>lambdaQuery()
                .eq(UserCoupon::getUserId, userId).eq(UserCoupon::getStatus, 0).orderByDesc(UserCoupon::getReceiveTime));
        CouponTemplate selectedTemplate = null;
        for (UserCoupon coupon : owned) {
            CouponTemplate template = templateMapper.selectById(coupon.getCouponId());
            List<Integer> eligible = eligibleLines(template, lines);
            BigDecimal amount = eligibleAmount(eligible, lines);
            String reason = unavailableReason(userId, coupon, template, amount);
            Map<String, Object> choice = new LinkedHashMap<>();
            choice.put("userCouponId", coupon.getId());
            choice.put("name", template == null ? "优惠券已失效" : template.getName());
            choice.put("couponType", template == null ? null : template.getCouponType());
            choice.put("discountType", template == null ? null : template.getDiscountType());
            choice.put("discountValue", template == null ? null : template.getDiscountValue());
            choice.put("minSpend", template == null ? null : template.getMinSpend());
            choice.put("expireTime", coupon.getExpireTime());
            choice.put("available", reason == null);
            choice.put("reason", reason);
            choice.put("discount", reason == null ? discount(template, amount) : ZERO);
            quote.getCoupons().add(choice);
            if (Objects.equals(selectedId, coupon.getId())) {
                if (reason == null) selectedTemplate = template;
                else quote.setSelectedCouponError(reason);
            }
        }
        if (selectedTemplate != null) apply(quote, selectedTemplate, selectedId, eligibleLines(selectedTemplate, lines), lines);
        if (selectedId != null && quote.getSelectedUserCouponId() == null && quote.getSelectedCouponError() == null)
            quote.setSelectedCouponError("优惠券已使用、已失效或不属于当前用户");
        return quote;
    }

    @Transactional(rollbackFor = Exception.class)
    public Quote settle(Integer userId, List<OrderInfo> lines, Long selectedId) {
        Quote quote = price(userId, lines);
        if (selectedId == null) return quote;
        UserCoupon coupon = userCouponMapper.selectForUpdate(selectedId);
        if (coupon == null || !Objects.equals(userId, coupon.getUserId())) throw new IllegalArgumentException("优惠券不属于当前用户");
        CouponTemplate template = templateMapper.selectForUpdate(coupon.getCouponId());
        List<Integer> eligible = eligibleLines(template, lines, true);
        String reason = unavailableReason(userId, coupon, template, eligibleAmount(eligible, lines));
        if (reason != null) throw new IllegalArgumentException(reason);
        apply(quote, template, selectedId, eligible, lines);
        if (userCouponMapper.redeem(selectedId, userId) != 1) throw new IllegalArgumentException("优惠券已使用或已过期，请重新选择");
        return quote;
    }

    private Quote price(Integer userId, List<OrderInfo> lines) {
        if (userId == null) throw new IllegalArgumentException("请先登录");
        if (lines == null || lines.isEmpty() || lines.size() > 100) throw new IllegalArgumentException("请选择1至100条商品进行结算");
        Quote quote = new Quote();
        Map<Integer, Integer> quantities = new HashMap<>();
        Map<Integer, Integer> tickets = new HashMap<>();
        for (OrderInfo line : lines) {
            if (line == null || line.getCommodityId() == null) throw new IllegalArgumentException("请选择有效商品");
            CommodityInfo goods = commodityService.getById(line.getCommodityId());
            if (goods == null || !"1".equals(goods.getOnPut()) || "1".equals(goods.getDelFlag()) || goods.getPrice() == null || goods.getPrice().signum() < 0)
                throw new IllegalArgumentException("商品不存在或已下架");
            int quantity = line.getCommodityNum() == null ? 1 : line.getCommodityNum();
            int ticketNum = line.getTicketNum() == null ? 0 : line.getTicketNum();
            if (quantity < 1 || quantity > 1000000 || ticketNum < 0 || ticketNum > quantity) throw new IllegalArgumentException("商品数量或水票数量无效");
            int totalQuantity = quantities.merge(goods.getId(), quantity, Integer::sum);
            if (goods.getStockNum() != null && totalQuantity > goods.getStockNum()) throw new IllegalArgumentException("商品库存不足");
            int totalTickets = tickets.merge(goods.getId(), ticketNum, Integer::sum);
            if (ticketNum > 0 && totalTickets > waterTicketService.available(userId, goods.getId())) throw new IllegalArgumentException("水票余额不足，请重新选择");
            BigDecimal total = goods.getPrice().multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal cash = goods.getPrice().multiply(BigDecimal.valueOf(quantity - ticketNum)).setScale(2, RoundingMode.HALF_UP);
            line.setCommodityNum(quantity).setTicketNum(ticketNum).setOrderPrice(cash)
                    .setUserCouponId(null).setCouponName(null).setCouponDiscount(ZERO);
            quote.setTotalPrice(quote.getTotalPrice().add(total));
            quote.setSubtotal(quote.getSubtotal().add(cash));
        }
        quote.setTicketDiscount(quote.getTotalPrice().subtract(quote.getSubtotal()));
        quote.setPayable(quote.getSubtotal());
        return quote;
    }

    private List<Integer> eligibleLines(CouponTemplate template, List<OrderInfo> lines) {
        return eligibleLines(template, lines, false);
    }

    private List<Integer> eligibleLines(CouponTemplate template, List<OrderInfo> lines, boolean lock) {
        if (template == null) return Collections.emptyList();
        Set<Integer> productIds = "PRODUCT".equals(template.getCouponType())
                ? relationMapper.selectList(Wrappers.<CouponCommodity>lambdaQuery().eq(CouponCommodity::getCouponId, template.getId())
                .last(lock ? "FOR UPDATE" : ""))
                .stream().map(CouponCommodity::getCommodityId).collect(Collectors.toSet()) : Collections.emptySet();
        List<Integer> eligible = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).getOrderPrice().signum() > 0 && ("ALL".equals(template.getCouponType()) || productIds.contains(lines.get(i).getCommodityId()))) eligible.add(i);
        }
        return eligible;
    }

    private BigDecimal eligibleAmount(List<Integer> eligible, List<OrderInfo> lines) {
        return eligible.stream().map(i -> lines.get(i).getOrderPrice()).reduce(ZERO, BigDecimal::add);
    }

    private String unavailableReason(Integer userId, UserCoupon coupon, CouponTemplate template, BigDecimal amount) {
        Date now = new Date();
        if (!Objects.equals(userId, coupon.getUserId())) return "优惠券不属于当前用户";
        if (!Integer.valueOf(0).equals(coupon.getStatus())) return "优惠券已使用或已失效";
        if (coupon.getExpireTime() == null || !coupon.getExpireTime().after(now)) return "优惠券已过期";
        if (template == null || !Integer.valueOf(1).equals(template.getStatus())) return "优惠券已停用或已删除";
        if (template.getStartTime() != null && now.before(template.getStartTime())) return "优惠券尚未生效";
        if (template.getEndTime() != null && !now.before(template.getEndTime())) return "优惠券活动已结束";
        if (amount.signum() <= 0) return "没有可抵扣的适用商品金额（水票抵扣后）";
        if (amount.compareTo(template.getMinSpend() == null ? ZERO : template.getMinSpend()) < 0) return "适用商品金额未达到使用门槛";
        if (discount(template, amount).signum() <= 0) return "当前优惠金额不足0.01元";
        return null;
    }

    private BigDecimal discount(CouponTemplate template, BigDecimal amount) {
        return ("RATE".equals(template.getDiscountType()) ? amount.subtract(amount.multiply(template.getDiscountValue()).setScale(2, RoundingMode.HALF_UP))
                : template.getDiscountValue().min(amount)).max(ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private void apply(Quote quote, CouponTemplate template, Long couponId, List<Integer> eligible, List<OrderInfo> lines) {
        BigDecimal amount = eligibleAmount(eligible, lines);
        BigDecimal saved = discount(template, amount);
        BigDecimal allocated = ZERO;
        // Cumulative rounding keeps the sum exact and never discounts an unrelated product.
        BigDecimal cumulative = ZERO;
        for (Integer index : eligible) {
            OrderInfo line = lines.get(index);
            cumulative = cumulative.add(line.getOrderPrice());
            BigDecimal next = saved.multiply(cumulative).divide(amount, 2, RoundingMode.HALF_UP);
            BigDecimal share = next.subtract(allocated);
            allocated = next;
            line.setCouponDiscount(share).setUserCouponId(couponId).setCouponName(template.getName()).setOrderPrice(line.getOrderPrice().subtract(share));
        }
        quote.setSelectedUserCouponId(couponId);
        quote.setCouponName(template.getName());
        quote.setCouponDiscount(saved);
        quote.setPayable(quote.getSubtotal().subtract(saved));
    }
}
