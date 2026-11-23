package cc.mrbird.febs.cos.controller;

import cc.mrbird.febs.common.utils.R;
import cc.mrbird.febs.cos.entity.MiniProgramBanner;
import cc.mrbird.febs.cos.service.MiniProgramService;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequestMapping("/system/mini-program")
@RequiredArgsConstructor
@RequiresPermissions("miniProgram:manage")
public class MiniProgramController {
    private final MiniProgramService service;
    @Value("${febs.uploadPath}")
    private String uploadPath;

    @GetMapping("/banners")
    public R banners() { return R.ok(service.banners(false)); }

    @RequestMapping(value = "/banners", method = {RequestMethod.POST, RequestMethod.PUT})
    public R save(MiniProgramBanner banner) { return R.ok(service.save(banner)); }

    @DeleteMapping("/banners/{id}")
    public R delete(@PathVariable Long id) { service.delete(id); return R.ok(); }

    @PostMapping("/images")
    public R upload(@RequestParam("image") MultipartFile image) throws IOException {
        if (image.isEmpty() || image.getSize() > 5 * 1024 * 1024) throw new IllegalArgumentException("请选择不超过5MB的图片");
        String format;
        try (InputStream stream = image.getInputStream(); ImageInputStream input = ImageIO.createImageInputStream(stream)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("仅支持 JPG、PNG、GIF 图片");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!Arrays.asList("jpeg", "jpg", "png", "gif").contains(format)) throw new IllegalArgumentException("仅支持 JPG、PNG、GIF 图片");
                if (reader.getWidth(0) > 12000 || reader.getHeight(0) > 12000 || (long) reader.getWidth(0) * reader.getHeight(0) > 16000000)
                    throw new IllegalArgumentException("图片宽高不能超过12000像素，总像素不能超过1600万");
                if (reader.read(0) == null) throw new IllegalArgumentException("图片文件无法读取");
            } finally { reader.dispose(); }
        } catch (IOException invalidImage) {
            throw new IllegalArgumentException("图片文件无法读取，请重新上传");
        }
        String filename = "banner-" + UUID.randomUUID().toString().replace("-", "") + "." + format;
        Path directory = Paths.get(uploadPath).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        try (InputStream stream = image.getInputStream()) { Files.copy(stream, directory.resolve(filename)); }
        return R.ok((Object) filename);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> invalidInput(IllegalArgumentException error) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 400); response.put("message", error.getMessage());
        return response;
    }
}
