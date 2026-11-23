package cc.mrbird.febs.cos.entity;

import lombok.Data;
import java.util.List;

@Data
public class CouponCheckoutRequest {
    private Integer userId;
    private Long userCouponId;
    // Only commodityId, commodityNum and ticketNum are used; client prices are ignored.
    private List<OrderInfo> lines;
}
