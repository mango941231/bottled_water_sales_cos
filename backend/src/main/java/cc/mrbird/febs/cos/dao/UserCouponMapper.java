package cc.mrbird.febs.cos.dao;

import cc.mrbird.febs.cos.entity.UserCoupon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface UserCouponMapper extends BaseMapper<UserCoupon> {
    @Select("SELECT * FROM user_coupon WHERE id = #{id} FOR UPDATE")
    UserCoupon selectForUpdate(@Param("id") Long id);

    @Update("UPDATE user_coupon SET status = 1 WHERE id = #{id} AND user_id = #{userId} AND status = 0 AND expire_time > NOW()")
    int redeem(@Param("id") Long id, @Param("userId") Integer userId);
}
