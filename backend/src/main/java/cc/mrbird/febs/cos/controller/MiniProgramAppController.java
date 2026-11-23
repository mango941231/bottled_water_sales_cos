package cc.mrbird.febs.cos.controller;

import cc.mrbird.febs.common.utils.R;
import cc.mrbird.febs.cos.service.MiniProgramService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mini-program")
@RequiredArgsConstructor
public class MiniProgramAppController {
    private final MiniProgramService service;

    @GetMapping("/banners")
    public R banners() { return R.ok(service.banners(true)); }
}
