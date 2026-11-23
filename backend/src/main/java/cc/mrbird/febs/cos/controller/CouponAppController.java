package cc.mrbird.febs.cos.controller;

import cc.mrbird.febs.common.utils.R;
import cc.mrbird.febs.cos.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/coupon")
@RequiredArgsConstructor
public class CouponAppController {
    private final CouponService couponService;

    @GetMapping("/list")
    public R list(@RequestParam(required = false) Integer userId,
                  @RequestParam(defaultValue = "all") String status) {
        return R.ok(couponService.listForApp(userId, status));
    }

    @PostMapping("/claim")
    public R claim(@RequestBody Map<String, Object> payload) {
        Integer userId = Integer.valueOf(String.valueOf(payload.get("userId")));
        Long couponId = Long.valueOf(String.valueOf(payload.get("couponId")));
        return R.ok(couponService.claim(userId, couponId));
    }
}
