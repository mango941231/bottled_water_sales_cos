package cc.mrbird.febs.cos.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@TableName("coupon_template")
public class CouponTemplate implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String couponType;
    private String discountType;
    private BigDecimal discountValue;
    private BigDecimal minSpend;
    private Integer validDays;
    private Date startTime;
    private Date endTime;
    private Integer totalCount;
    private Integer claimedCount;
    private Integer status;
    private Date createTime;
    @TableField(exist = false)
    private List<Integer> commodityIds;
    @TableField(exist = false)
    private String commodityNames;
}
