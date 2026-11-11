<template>
  <a-skeleton active :loading="loading" :paragraph="{rows: 17}">
    <div class="jvm-info">
    <a-alert v-if="metricError" type="error" :message="metricError" show-icon />
    <a-alert v-if="unavailableMetrics.length" type="warning" :message="'当前运行环境未提供部分指标：' + unavailableMetrics.join('、')" show-icon />
      <div class="alert">
        <a-alert type="success" :show-icon="true">
          <div slot="message">
            数据获取时间 {{this.time}}
            <a style="margin-left: 24px" @click="create">点击刷新</a>
          </div>
        </a-alert>
      </div>
      <table>
        <tr>
          <th>参数</th>
          <th>描述</th>
          <th>当前值</th>
        </tr>
        <tr>
          <td><a-tag color="green">system.cpu.count</a-tag></td>
          <td>CPU 数量</td>
          <td>{{system.cpu.count}} 核</td>
        </tr>
        <tr>
          <td><a-tag color="green">system.cpu.usage</a-tag></td>
          <td>系统 CPU 使用率</td>
          <td>{{system.cpu.usage}} %</td>
        </tr>
        <tr>
          <td><a-tag color="purple">process.start.time</a-tag></td>
          <td>应用启动时间点</td>
          <td>{{system.process.startTime}}</td>
        </tr>
        <tr>
          <td><a-tag color="purple">process.uptime</a-tag></td>
          <td>应用已运行时间</td>
          <td>{{system.process.uptime}} 秒</td>
        </tr>
        <tr>
          <td><a-tag color="purple">process.cpu.usage</a-tag></td>
          <td>当前应用 CPU 使用率</td>
          <td>{{system.process.cpuUsage}} %</td>
        </tr>
      </table>
    </div>
  </a-skeleton>
</template>
<script>
import {getMetrics} from '@/utils/monitorMetrics'
import moment from 'moment'
moment.locale('zh-cn')

export default {
  data () {
    return {
      time: '',
      loading: true,
      metricError: '',
      unavailableMetrics: [],
      system: {
        cpu: {
          count: 0,
          usage: 0
        },
        process: {
          cpuUsage: 0,
          uptime: 0,
          startTime: 0
        }
      }
    }
  },
  mounted () {
    this.create()
  },
  methods: {
    create () {
      this.time = moment().format('YYYY年MM月DD日 HH时mm分ss秒')
      this.metricError = ''
      this.loading = true
      getMetrics(this.$get, [
        'system.cpu.count',
        'system.cpu.usage',
        'process.uptime',
        'process.start.time',
        'process.cpu.usage'
      ]).then((r) => {
        this.unavailableMetrics = r.filter(item => item.data.unavailable).map(item => item.data.name)
        this.system.cpu.count = r[0].data.measurements[0].value
        this.system.cpu.usage = this.convert(r[1].data.measurements[0].value)
        this.system.process.uptime = r[2].data.measurements[0].value
        this.system.process.startTime = typeof r[3].data.measurements[0].value === 'number' ? moment(r[3].data.measurements[0].value * 1000).format('YYYY-MM-DD HH:mm:ss') : '-'
        this.system.process.cpuUsage = this.convert(r[4].data.measurements[0].value)
        this.loading = false
      }).catch((r) => {
        this.loading = false
        this.metricError = '无法获取监控指标，请检查权限或稍后重试'
        console.error(r)
        this.$message.error('获取服务器信息失败')
      })
    },
    convert (value) {
      if (typeof value !== 'number') return '-'
      return Number(value * 100).toFixed(2)
    }
  }
}
</script>
<style lang="less">
  .jvm-info {
    width: 100%;
    table {
      width: 100%;
      tr {
        line-height: 1.5rem;
        border-bottom: 1px solid #f1f1f1;
        th {
          background: #fafafa;
          padding: .5rem;
        }
        td {
          padding: .5rem;
          .ant-tag {
            font-size: .8rem !important;
          }
        }
      }
    }
    .alert {
      margin-bottom: .5rem;
    }
  }
</style>
