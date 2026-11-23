package cc.mrbird.febs.cos.controller;

import cc.mrbird.febs.common.utils.FebsUtil;
import cc.mrbird.febs.common.utils.R;
import cc.mrbird.febs.cos.entity.CouponTemplate;
import cc.mrbird.febs.cos.service.CouponService;
import cc.mrbird.febs.system.domain.User;
import cn.hutool.core.date.DateUtil;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.authz.UnauthorizedException;
import org.springframework.http.HttpStatus;
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

    @GetMapping("/admin/page")
    public R page(@RequestParam(defaultValue = "1") long current, @RequestParam(defaultValue = "10") long size,
                  @RequestParam(required = false) String name, @RequestParam(required = false) String couponType,
                  @RequestParam(required = false) Integer status) {
        requireAdmin();
        return R.ok(couponService.pageForAdmin(current, size, name, couponType, status));
    }

    @GetMapping("/admin/{id}")
    public R detail(@PathVariable Long id) { requireAdmin(); return R.ok(couponService.detailForAdmin(id)); }

    @RequestMapping(value = "/admin", method = {RequestMethod.POST, RequestMethod.PUT})
    public R save(@RequestParam Map<String, String> params) {
        requireAdmin();
        CouponTemplate template = new CouponTemplate();
        if (params.get("id") != null && !params.get("id").isEmpty()) template.setId(Long.valueOf(params.get("id")));
        template.setName(params.get("name")); template.setCouponType(params.get("couponType"));
        template.setDiscountType(params.get("discountType"));
        if (params.get("discountValue") == null || params.get("validDays") == null) throw new IllegalArgumentException("请填写优惠金额和有效期");
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
    public R delete(@PathVariable Long id) { requireAdmin(); couponService.delete(id); return R.ok(); }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> invalidInput(IllegalArgumentException error) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 400);
        response.put("message", error instanceof NumberFormatException ? "数量或金额格式无效" : error.getMessage());
        return response;
    }

    private void requireAdmin() {
        User user = FebsUtil.getCurrentUser();
        String roleIds = user == null ? null : user.getRoleId();
        if (roleIds == null || !java.util.Arrays.stream(roleIds.split(","))
                .map(String::trim).anyMatch(id -> "1".equals(id) || "74".equals(id))) {
            throw new UnauthorizedException("仅管理员可管理优惠券");
        }
    }

    private Date parseDate(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return DateUtil.parse(value.trim());
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("活动时间格式无效，请选择有效日期");
        }
    }
}
