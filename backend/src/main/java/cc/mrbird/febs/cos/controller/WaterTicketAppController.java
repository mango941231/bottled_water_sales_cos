package cc.mrbird.febs.cos.controller;

import cc.mrbird.febs.common.utils.R;
import cc.mrbird.febs.cos.entity.WaterTicketPackage;
import cc.mrbird.febs.cos.service.WaterTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/waterTicket")
@RequiredArgsConstructor
public class WaterTicketAppController {
    private final WaterTicketService waterTicketService;

    @GetMapping("/packages")
    public R packages(@RequestParam Integer commodityId) {
        return R.ok(waterTicketService.listForCommodity(commodityId));
    }

    @PostMapping("/purchase")
    public R purchase(@RequestBody Map<String, Integer> payload) {
        return R.ok(waterTicketService.purchase(payload.get("userId"), payload.get("packageId")));
    }

    @GetMapping("/mine")
    public R mine(@RequestParam Integer userId) {
        return R.ok(waterTicketService.listAccounts(userId));
    }

    @GetMapping("/available")
    public R available(@RequestParam Integer userId, @RequestParam Integer commodityId) {
        return R.ok(waterTicketService.available(userId, commodityId));
    }
}
