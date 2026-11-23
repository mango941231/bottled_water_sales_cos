<template><coupon-management /></template>

<script>
// Keep old menu component paths usable until the menu upgrade is applied.
import CouponManagement from '../coupon/Coupon'

export default {
  name: 'CouponLegacy',
  components: { CouponManagement }
}
</script>
