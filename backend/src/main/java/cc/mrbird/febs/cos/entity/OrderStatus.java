package cc.mrbird.febs.cos.entity;

/** Shared order lifecycle; payment and receipt are separate transitions. */
public final class OrderStatus {
    public static final int UNPAID = 0;
    public static final int PENDING_DELIVERY = 1;
    public static final int CANCELLED = 2;
    public static final int COMPLETED = 3;
    public static final int CLOSED = 4;
    private OrderStatus() { }
}
