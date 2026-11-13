package cc.mrbird.febs.cos.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("water_ticket_account")
public class WaterTicketAccount implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Integer userId;
    private Integer shopId;
    private Integer commodityId;
    private Integer packageId;
    private String packageName;
    private BigDecimal purchasePrice;
    private Integer totalNum;
    private Integer remainingNum;
    private Date expireTime;
    private Integer status;
    private Date createTime;
}
