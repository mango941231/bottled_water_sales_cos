package cc.mrbird.febs.cos.dao;

import cc.mrbird.febs.cos.entity.WaterTicketAccount;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface WaterTicketAccountMapper extends BaseMapper<WaterTicketAccount> {
    @Update("UPDATE water_ticket_account SET remaining_num = remaining_num - #{num} " +
            "WHERE id = #{id} AND remaining_num >= #{num} AND status = 1 AND expire_time > NOW()")
    int consume(@Param("id") Long id, @Param("num") int num);
}
