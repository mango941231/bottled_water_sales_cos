package cc.mrbird.febs.cos.service.impl;

import cc.mrbird.febs.cos.entity.CommodityInfo;
import cc.mrbird.febs.cos.dao.CommodityInfoMapper;
import cc.mrbird.febs.cos.service.ICommodityInfoService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * @author FanK
 */
@Service
public class CommodityInfoServiceImpl extends ServiceImpl<CommodityInfoMapper, CommodityInfo> implements ICommodityInfoService {

    /**
     * 分页查询商品信息
     *
     * @param page          分页对象
     * @param commodityInfo 商品信息
     * @return 结果
     */
    @Override
    public IPage<LinkedHashMap<String, Object>> getCommodityByPage(Page<CommodityInfo> page, CommodityInfo commodityInfo) {
        return baseMapper.getCommodityByPage(page, commodityInfo);
    }

    /**
     * 小程序热销产品
     *
     * @return 结果
     */
    @Override
    public List<LinkedHashMap<String, Object>> getCommodityHot() {
        return baseMapper.getCommodityHot();
    }

    /**
     * 商品详情
     *
     * @param commodityId 商品ID
     * @return 结果
     */
    @Override
    public LinkedHashMap<String, Object> goodsDetail(Integer commodityId) {
        return baseMapper.goodsDetail(commodityId);
    }

    /**
     * 搜索商品
     *
     * @param key 商品
     * @return 结果
     */
    @Override
    public LinkedHashMap<String, Object> getGoodsFuzzy(String key) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("commodity", baseMapper.catalog(key, null));
        return result;
    }

}
