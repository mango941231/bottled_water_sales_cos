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
        <td><a-tag color="purple">jvm.memory.max</a-tag></td>
        <td>JVM 最大内存</td>
        <td>{{jvm.memory.max}} MB</td>
      </tr>
      <tr>
        <td><a-tag color="purple">jvm.memory.committed</a-tag></td>
        <td>JVM 可用内存</td>
        <td>{{jvm.memory.committed}} MB</td>
      </tr>
      <tr>
        <td><a-tag color="purple">jvm.memory.used</a-tag></td>
        <td>JVM 已用内存</td>
        <td>{{jvm.memory.used}} MB</td>
      </tr>
      <tr>
        <td><a-tag color="cyan">jvm.buffer.memory.used</a-tag></td>
        <td>JVM 缓冲区已用内存</td>
        <td>{{jvm.buffer.memory.used}} MB</td>
      </tr>
      <tr>
        <td><a-tag color="cyan">jvm.buffer.count</a-tag></td>
        <td>当前缓冲区数量</td>
        <td>{{jvm.buffer.count}} 个</td>
      </tr>
      <tr>
        <td><a-tag color="green">jvm.threads.daemon</a-tag></td>
        <td>JVM 守护线程数量</td>
        <td>{{jvm.threads.daemon}} 个</td>
      </tr>
      <tr>
        <td><a-tag color="green">jvm.threads.live</a-tag></td>
        <td>JVM 当前活跃线程数量</td>
        <td>{{jvm.threads.live}} 个</td>
      </tr>
      <tr>
        <td><a-tag color="green">jvm.threads.peak</a-tag></td>
        <td>JVM 峰值线程数量</td>
        <td>{{jvm.threads.peak}} 个</td>
      </tr>
      <tr>
        <td><a-tag color="orange">jvm.classes.loaded</a-tag></td>
        <td>JVM 已加载 Class 数量</td>
        <td>{{jvm.classes.loaded}} 个</td>
      </tr>
      <tr>
        <td><a-tag color="orange">jvm.classes.unloaded</a-tag></td>
        <td>JVM 未加载 Class 数量</td>
        <td>{{jvm.classes.unloaded}} 个</td>
      </tr>
      <tr>
        <td><a-tag color="pink">jvm.gc.memory.allocated</a-tag></td>
        <td>GC 时, 年轻代分配的内存空间</td>
        <td>{{jvm.gc.memory.allocated}} MB</td>
      </tr>
      <tr>
        <td><a-tag color="pink">jvm.gc.memory.promoted</a-tag></td>
        <td>GC 时, 老年代分配的内存空间</td>
        <td>{{jvm.gc.memory.promoted}} MB</td>
      </tr>
      <tr>
        <td><a-tag color="pink">jvm.gc.max.data.size</a-tag></td>
        <td>GC 时, 老年代的最大内存空间</td>
        <td>{{jvm.gc.maxDataSize}} MB</td>
      </tr>
      <tr>
        <td><a-tag color="pink">jvm.gc.live.data.size</a-tag></td>
        <td>FullGC 时, 老年代的内存空间</td>
        <td>{{jvm.gc.liveDataSize}} MB</td>
      </tr>
      <tr>
        <td><a-tag color="blue">jvm.gc.pause.count</a-tag></td>
        <td>系统启动以来GC 次数</td>
        <td>{{jvm.gc.pause.count}} 次</td>
      </tr>
      <tr>
        <td><a-tag color="blue">jvm.gc.pause.totalTime</a-tag></td>
        <td>系统启动以来GC 总耗时</td>
        <td>{{jvm.gc.pause.totalTime}} 秒</td>
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
      jvm: {
        memory: {
          max: 0,
          committed: 0,
          used: 0
        },
        buffer: {
          memory: {
            used: 0
          },
          count: 0
        },
        threads: {
          daemon: 0,
          live: 0,
          peak: 0
        },
        classes: {
          loaded: 0,
          unloaded: 0
        },
        gc: {
          memory: {
            allocated: 0,
            promoted: 0
          },
          maxDataSize: 0,
          liveDataSize: 0,
          pause: {
            totalTime: 0,
            count: 0
          }
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
        'jvm.memory.max',
        'jvm.memory.committed',
        'jvm.memory.used',
        'jvm.buffer.memory.used',
        'jvm.buffer.count',
        'jvm.threads.daemon',
        'jvm.threads.live',
        'jvm.threads.peak',
        'jvm.classes.loaded',
        'jvm.classes.unloaded',
        'jvm.gc.memory.allocated',
        'jvm.gc.memory.promoted',
        'jvm.gc.max.data.size',
        'jvm.gc.live.data.size',
        'jvm.gc.pause'
      ]).then((r) => {
        this.unavailableMetrics = r.filter(item => item.data.unavailable).map(item => item.data.name)
        this.jvm.memory.max = this.convert(r[0].data.measurements[0].value)
        this.jvm.memory.committed = this.convert(r[1].data.measurements[0].value)
        this.jvm.memory.used = this.convert(r[2].data.measurements[0].value)
        this.jvm.buffer.memory.used = this.convert(r[3].data.measurements[0].value)
        this.jvm.buffer.count = r[4].data.measurements[0].value
        this.jvm.threads.daemon = r[5].data.measurements[0].value
        this.jvm.threads.live = r[6].data.measurements[0].value
        this.jvm.threads.peak = r[7].data.measurements[0].value
        this.jvm.classes.loaded = r[8].data.measurements[0].value
        this.jvm.classes.unloaded = r[9].data.measurements[0].value
        this.jvm.gc.memory.allocated = this.convert(r[10].data.measurements[0].value)
        this.jvm.gc.memory.promoted = this.convert(r[11].data.measurements[0].value)
        this.jvm.gc.maxDataSize = this.convert(r[12].data.measurements[0].value)
        this.jvm.gc.liveDataSize = this.convert(r[13].data.measurements[0].value)
        this.jvm.gc.pause.count = r[14].data.measurements[0].value
        this.jvm.gc.pause.totalTime = r[14].data.measurements[1].value
        this.loading = false
      }).catch((r) => {
        this.loading = false
        this.metricError = '无法获取监控指标，请检查权限或稍后重试'
        console.error(r)
        this.$message.error('获取JVM信息失败')
      })
    },
    convert (value) {
      if (typeof value !== 'number') return '-'
      return Number(value / 1048576).toFixed(3)
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
