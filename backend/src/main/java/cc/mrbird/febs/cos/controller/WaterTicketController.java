package cc.mrbird.febs.cos.controller;

import cc.mrbird.febs.common.utils.FebsUtil;
import cc.mrbird.febs.common.utils.R;
import cc.mrbird.febs.cos.entity.WaterTicketPackage;
import cc.mrbird.febs.cos.service.WaterTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cos/water-ticket")
@RequiredArgsConstructor
public class WaterTicketController {
    private final WaterTicketService waterTicketService;

    @GetMapping("/packages")
    public R listPackages() {
        return R.ok(waterTicketService.listForShopUser(FebsUtil.getCurrentUser().getUserId().intValue()));
    }

    @PostMapping("/packages")
    public R savePackage(@RequestBody WaterTicketPackage ticketPackage) {
        return R.ok(waterTicketService.savePackage(FebsUtil.getCurrentUser().getUserId().intValue(), ticketPackage));
    }

    @DeleteMapping("/packages/{id}")
    public R deletePackage(@PathVariable Integer id) {
        WaterTicketPackage old = waterTicketService.listForShopUser(FebsUtil.getCurrentUser().getUserId().intValue())
                .stream().filter(item -> id.equals(item.getId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("水票套餐不存在或无权删除"));
        old.setStatus(0);
        return R.ok(waterTicketService.savePackage(FebsUtil.getCurrentUser().getUserId().intValue(), old));
    }
}
