package cc.mrbird.febs.cos.dao;

import cc.mrbird.febs.cos.entity.CouponTemplate;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface CouponTemplateMapper extends BaseMapper<CouponTemplate> {
    @Update("UPDATE coupon_template SET claimed_count = claimed_count + 1 WHERE id = #{id} AND status = 1 AND (total_count IS NULL OR claimed_count < total_count)")
    int incrementClaimed(@Param("id") Long id);
}
