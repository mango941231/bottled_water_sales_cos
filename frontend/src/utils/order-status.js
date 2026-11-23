export const orderStatusLabel = value => ({'0': '待支付', '1': '待配送', '2': '已取消', '3': '已完成', '4': '已关闭'}[String(value)] || '- -')
