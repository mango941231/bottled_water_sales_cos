package cc.mrbird.febs.cos.dao;

import cc.mrbird.febs.cos.entity.CommodityInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * @author FanK
 */
public interface CommodityInfoMapper extends BaseMapper<CommodityInfo> {

    /**
     * 分页查询商品信息
     *
     * @param page          分页对象
     * @param commodityInfo 商品信息
     * @return 结果
     */
    IPage<LinkedHashMap<String, Object>> getCommodityByPage(Page<CommodityInfo> page, @Param("commodityInfo") CommodityInfo commodityInfo);

    /**
     * 小程序热销产品
     *
     * @return 结果
     */
    List<LinkedHashMap<String, Object>> getCommodityHot();

    /**
     * 商品详情
     *
     * @param commodityId 商品ID
     * @return 结果
     */
    LinkedHashMap<String, Object> goodsDetail(@Param("commodityId") Integer commodityId);







    List<LinkedHashMap<String, Object>> catalog(@Param("key") String key, @Param("sort") Integer sort);
}
