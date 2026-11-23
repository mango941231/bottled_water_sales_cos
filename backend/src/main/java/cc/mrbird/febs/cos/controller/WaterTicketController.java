package cc.mrbird.febs.cos.controller;

import cc.mrbird.febs.common.utils.FebsUtil;
import cc.mrbird.febs.common.utils.R;
import cc.mrbird.febs.cos.entity.WaterTicketPackage;
import cc.mrbird.febs.cos.service.WaterTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.apache.shiro.authz.UnauthorizedException;
import cc.mrbird.febs.system.domain.User;

import java.util.List;

@RestController
@RequestMapping("/cos/water-ticket")
@RequiredArgsConstructor
public class WaterTicketController {
    private final WaterTicketService waterTicketService;

    @GetMapping("/admin/packages")
    public R listAllPackagesForAdmin() {
        requirePlatformAdmin();
        return R.ok(waterTicketService.listAllPackages());
    }

    @PostMapping("/admin/packages")
    public R savePackageForAdmin(WaterTicketPackage ticketPackage) {
        requirePlatformAdmin();
        return R.ok(waterTicketService.savePackageAsAdmin(ticketPackage));
    }

    @DeleteMapping("/admin/packages/{id}")
    public R deletePackageForAdmin(@PathVariable Integer id) {
        requirePlatformAdmin();
        return R.ok(waterTicketService.disablePackageAsAdmin(id));
    }

    @GetMapping("/admin/accounts/{userId}")
    public R listUserAccounts(@PathVariable Integer userId) {
        requirePlatformAdmin();
        return R.ok(waterTicketService.listAccounts(userId));
    }

    private void requirePlatformAdmin() {
        User user = FebsUtil.getCurrentUser();
        String roleIds = user.getRoleId();
        if (roleIds == null || !java.util.Arrays.stream(roleIds.split(","))
                .map(String::trim).anyMatch(id -> "1".equals(id) || "74".equals(id))) {
            throw new UnauthorizedException("仅管理员可管理水票");
        }
    }
}
