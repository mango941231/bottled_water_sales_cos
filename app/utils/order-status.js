const labels = {0: '待支付', 1: '待配送', 2: '已取消', 3: '已完成', 4: '已关闭'}
module.exports = { label: value => labels[String(value)] || '未知状态' }
