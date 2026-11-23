package cc.mrbird.febs.cos.service;

import cc.mrbird.febs.cos.dao.MiniProgramBannerMapper;
import cc.mrbird.febs.cos.entity.MiniProgramBanner;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MiniProgramService {
    private final MiniProgramBannerMapper bannerMapper;

    public List<MiniProgramBanner> banners(boolean enabledOnly) {
        return bannerMapper.selectList(Wrappers.<MiniProgramBanner>lambdaQuery()
                .eq(enabledOnly, MiniProgramBanner::getStatus, 1)
                .orderByAsc(MiniProgramBanner::getSortNum).orderByAsc(MiniProgramBanner::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public MiniProgramBanner save(MiniProgramBanner banner) {
        String title = banner.getTitle() == null ? "" : banner.getTitle().trim();
        String image = banner.getImageUrl() == null ? "" : banner.getImageUrl().trim();
        if (title.length() > 100) throw new IllegalArgumentException("轮播图名称最多100字");
        if (!validImageUrl(image)) throw new IllegalArgumentException("请上传图片或填写有效的 HTTP/HTTPS 图片地址");
        if (banner.getSortNum() == null) banner.setSortNum(0);
        if (banner.getSortNum() < 0 || banner.getSortNum() > 9999) throw new IllegalArgumentException("排序值须在0至9999之间");
        if (banner.getStatus() == null) banner.setStatus(1);
        if (banner.getStatus() != 0 && banner.getStatus() != 1) throw new IllegalArgumentException("启用状态无效");
        banner.setTitle(title); banner.setImageUrl(image); banner.setUpdateTime(new Date());
        if (banner.getId() == null) {
            banner.setCreateTime(banner.getUpdateTime());
            if (bannerMapper.insert(banner) != 1) throw new IllegalStateException("轮播图保存失败，请重试");
        } else {
            MiniProgramBanner existing = bannerMapper.selectById(banner.getId());
            if (existing == null) throw new IllegalArgumentException("轮播图不存在，请刷新列表");
            banner.setCreateTime(existing.getCreateTime());
            if (bannerMapper.updateById(banner) != 1) throw new IllegalStateException("轮播图保存失败，请重试");
        }
        return banner;
    }

    public void delete(Long id) {
        if (id == null || bannerMapper.deleteById(id) != 1) throw new IllegalArgumentException("轮播图不存在，请刷新列表");
    }

    private boolean validImageUrl(String value) {
        if (value.isEmpty() || value.length() > 1000 || value.contains("..")) return false;
        // Uploaded images are stored by filename; external image addresses are never fetched by the server.
        if (value.matches("[A-Za-z0-9][A-Za-z0-9._-]*\\.(?i:jpg|jpeg|png|gif|webp)")) return true;
        try {
            URI uri = new URI(value);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (Exception invalid) { return false; }
    }
}
