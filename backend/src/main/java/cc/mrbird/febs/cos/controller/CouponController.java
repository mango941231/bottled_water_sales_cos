package cc.mrbird.febs.cos.controller;

import cc.mrbird.febs.common.utils.FebsUtil;
import cc.mrbird.febs.common.utils.R;
import cc.mrbird.febs.cos.entity.CouponTemplate;
import cc.mrbird.febs.cos.service.CouponService;
import cc.mrbird.febs.system.domain.User;
import cn.hutool.core.date.DateUtil;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.authz.UnauthorizedException;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/cos/coupon")
@RequiredArgsConstructor
public class CouponController {
    private final CouponService couponService;

    @GetMapping("/admin/list")
    public R list() { requireAdmin(); return R.ok(couponService.listForAdmin()); }

    @PostMapping("/admin")
    public R save(@RequestParam Map<String, String> params) {
        requireAdmin();
        CouponTemplate template = new CouponTemplate();
        if (params.get("id") != null && !params.get("id").isEmpty()) template.setId(Long.valueOf(params.get("id")));
        template.setName(params.get("name")); template.setCouponType(params.get("couponType"));
        template.setDiscountType(params.get("discountType"));
        template.setDiscountValue(new BigDecimal(params.get("discountValue")));
        template.setMinSpend(params.get("minSpend") == null || params.get("minSpend").isEmpty() ? BigDecimal.ZERO : new BigDecimal(params.get("minSpend")));
        template.setValidDays(Integer.valueOf(params.get("validDays")));
        template.setStartTime(parseDate(params.get("startTime"))); template.setEndTime(parseDate(params.get("endTime")));
        template.setTotalCount(params.get("totalCount") == null || params.get("totalCount").isEmpty() ? null : Integer.valueOf(params.get("totalCount")));
        template.setStatus(params.get("status") == null || params.get("status").isEmpty() ? 1 : Integer.valueOf(params.get("status")));
        String ids = params.get("commodityIds");
        if (ids != null && !ids.trim().isEmpty()) {
            List<Integer> commodityIds = new ArrayList<>();
            for (String id : ids.split(",")) commodityIds.add(Integer.valueOf(id.trim()));
            template.setCommodityIds(commodityIds);
        }
        return R.ok(couponService.save(template));
    }

    @DeleteMapping("/admin/{id}")
    public R disable(@PathVariable Long id) { requireAdmin(); couponService.disable(id); return R.ok(); }

    private void requireAdmin() {
        User user = FebsUtil.getCurrentUser();
        String roleIds = user == null ? null : user.getRoleId();
        if (roleIds == null || !java.util.Arrays.stream(roleIds.split(","))
                .map(String::trim).anyMatch(id -> "1".equals(id) || "74".equals(id))) {
            throw new UnauthorizedException("仅管理员可管理优惠券");
        }
    }

    private Date parseDate(String value) {
        return value == null || value.trim().isEmpty() ? null : DateUtil.parse(value.trim());
    }
}
