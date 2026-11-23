<template>
  <a-card :bordered="false" class="card-area order-list">
    <div :class="advanced ? 'search' : null">
      <!-- 搜索区域 -->
      <a-form layout="horizontal">
        <a-row :gutter="15">
          <div :class="advanced ? null: 'fold'">
            <a-col :md="6" :sm="24">
              <a-form-item
                label='商品名称'
                :labelCol="{span: 5}"
                :wrapperCol="{span: 18, offset: 1}">
                <a-input v-model="queryParams.commodityName"/>
              </a-form-item>
            </a-col>
            <a-col :md="6" :sm="24">
              <a-form-item
                label="购买人"
                :labelCol="{span: 5}"
                :wrapperCol="{span: 18, offset: 1}">
                <a-input v-model="queryParams.userName"/>
              </a-form-item>
            </a-col>
          </div>
          <span style="float: right; margin-top: 3px;">
            <a-button type="primary" @click="search">查询</a-button>
            <a-button style="margin-left: 8px" @click="reset">重置</a-button>
          </span>
        </a-row>
      </a-form>
    </div>
    <div>
      <div class="operator">
        <!--        <a-button type="primary" ghost @click="add">新增</a-button>-->
        <a-button @click="batchDelete">删除</a-button>
      </div>
      <!-- 表格区域 -->
      <a-table ref="TableInfo"
               :columns="columns"
               :rowKey="record => record.id"
               :dataSource="dataSource"
               :pagination="pagination"
               :loading="loading"
               :rowSelection="{selectedRowKeys: selectedRowKeys, onChange: onSelectChange}"
               :scroll="{ x: 1660 }"
               :rowClassName="orderRowClass"
               @change="handleTableChange">
        <template slot="userNameShow" slot-scope="text, record">
          <a-popover>
            <template slot="content">
              <a-avatar shape="square" size={132} icon="user" :src="record.avatar" />
            </template>
            <a>{{ record.userName || '- -' }}</a>
          </a-popover>
        </template>
        <template slot="addressShow" slot-scope="text, record">
          <template>
            <a-tooltip>
              <template slot="title">
                {{ record.address }}
              </template>
              <span class="order-cell">{{ record.address || '- -' }}</span>
            </a-tooltip>
          </template>
        </template>
        <template slot="operation" slot-scope="text, record">
          <a-icon type="reconciliation" @click="view(record)" title="查 看"></a-icon>
        </template>
      </a-table>
    </div>
    <order-view
      @close="handleOrderViewClose"
      @checkClose="handleOrderCheckClose"
      :orderShow="orderView.visiable"
      :orderData="orderView.data">
    </order-view>
  </a-card>
</template>

<script>
import {orderStatusLabel} from '@/utils/order-status'
import RangeDate from '@/components/datetime/RangeDate'
import {mapState} from 'vuex'
import moment from 'moment'
import OrderView from './OrderView'
moment.locale('zh-cn')

export default {
  name: 'order',
  components: {OrderView, RangeDate},
  data () {
    return {
      orderView: {
        visiable: false,
        data: null
      },
      advanced: false,
      queryParams: {},
      filteredInfo: null,
      sortedInfo: null,
      paginationInfo: null,
      dataSource: [],
      selectedRowKeys: [],
      loading: false,
      pagination: {
        pageSizeOptions: ['10', '20', '30', '40', '100'],
        defaultCurrent: 1,
        defaultPageSize: 10,
        showQuickJumper: true,
        showSizeChanger: true,
        showTotal: (total, range) => `显示 ${range[0]} ~ ${range[1]} 条记录，共 ${total} 条记录`
      },
      userList: []
    }
  },
  computed: {
    ...mapState({
      currentUser: state => state.account.user
    }),
    columns () {
      const textOrEmpty = text => text === null || text === undefined || text === '' ? '- -' : text
      return [{
        title: '下单时间', dataIndex: 'createDate', width: 180, customRender: textOrEmpty
      }, {
        title: '购买数量',
        dataIndex: 'commodityNum',
        width: 120,
        customRender: text => `${Number(text || 1)} 桶`
      }, {
        title: '订单价格',
        dataIndex: 'orderPrice',
        width: 130,
        customRender: text => text === null || text === undefined ? '- -' : '￥' + Number(text).toFixed(2)
      }, {
        title: '水票数量',
        dataIndex: 'ticketNum',
        width: 120,
        customRender: text => `${Number(text || 0)} 张`
      }, {
        title: '购买人',
        dataIndex: 'userName',
        width: 140,
        scopedSlots: { customRender: 'userNameShow' }
      }, {
        title: '收货人', dataIndex: 'taskUserName', width: 130, customRender: textOrEmpty
      }, {
        title: '联系方式', dataIndex: 'phone', width: 150, customRender: textOrEmpty
      }, {
        title: '收货地址',
        dataIndex: 'address',
        width: 300,
        scopedSlots: { customRender: 'addressShow' }
      }, {
        title: '订单状态',
        dataIndex: 'orderStatus',
        width: 110,
        customRender: orderStatusLabel
      }, {
        title: '操作',
        dataIndex: 'operation',
        width: 80,
        fixed: 'right',
        scopedSlots: {customRender: 'operation'}
      }]
    }
  },
  mounted () {
    this.fetch()
  },
  methods: {
    orderRowClass (record) {
      switch (String(record.orderStatus)) {
        case '3':
          return 'order-row-completed'
        case '1':
          return 'order-row-pending'
        case '2':
        case '4':
          return 'order-row-cancelled'
        case '0':
          return 'order-row-unpaid'
        default:
          return ''
      }
    },
    view (row) {
      this.orderView.data = row
      this.orderView.visiable = true
    },
    handleOrderViewClose () {
      this.orderView.visiable = false
    },
    handleOrderCheckClose () {
      this.orderView.visiable = false
      this.$message.success('审核成功')
      this.fetch()
    },
    onSelectChange (selectedRowKeys) {
      this.selectedRowKeys = selectedRowKeys
    },
    toggleAdvanced () {
      this.advanced = !this.advanced
    },
    handleDeptChange (value) {
      this.queryParams.deptId = value || ''
    },
    batchDelete () {
      if (!this.selectedRowKeys.length) {
        this.$message.warning('请选择需要删除的记录')
        return
      }
      let that = this
      this.$confirm({
        title: '确定删除所选中的记录?',
        content: '当您点击确定按钮后，这些记录将会被彻底删除',
        centered: true,
        onOk () {
          let ids = that.selectedRowKeys.join(',')
          that.$delete('/cos/order-info/' + ids).then(() => {
            that.$message.success('删除成功')
            that.selectedRowKeys = []
            that.search()
          })
        },
        onCancel () {
          that.selectedRowKeys = []
        }
      })
    },
    search () {
      let {sortedInfo, filteredInfo} = this
      let sortField, sortOrder
      // 获取当前列的排序和列的过滤规则
      if (sortedInfo) {
        sortField = sortedInfo.field
        sortOrder = sortedInfo.order
      }
      this.fetch({
        sortField: sortField,
        sortOrder: sortOrder,
        ...this.queryParams,
        ...filteredInfo
      })
    },
    reset () {
      // 取消选中
      this.selectedRowKeys = []
      // 重置分页
      this.$refs.TableInfo.pagination.current = this.pagination.defaultCurrent
      if (this.paginationInfo) {
        this.paginationInfo.current = this.pagination.defaultCurrent
        this.paginationInfo.pageSize = this.pagination.defaultPageSize
      }
      // 重置列过滤器规则
      this.filteredInfo = null
      // 重置列排序规则
      this.sortedInfo = null
      // 重置查询参数
      this.queryParams = {}
      this.fetch()
    },
    handleTableChange (pagination, filters, sorter) {
      // 将这三个参数赋值给Vue data，用于后续使用
      this.paginationInfo = pagination
      this.filteredInfo = filters
      this.sortedInfo = sorter

      this.fetch({
        sortField: sorter.field,
        sortOrder: sorter.order,
        ...this.queryParams,
        ...filters
      })
    },
    fetch (params = {}) {
      // 显示loading
      this.loading = true
      if (this.paginationInfo) {
        // 如果分页信息不为空，则设置表格当前第几页，每页条数，并设置查询分页参数
        this.$refs.TableInfo.pagination.current = this.paginationInfo.current
        this.$refs.TableInfo.pagination.pageSize = this.paginationInfo.pageSize
        params.size = this.paginationInfo.pageSize
        params.current = this.paginationInfo.current
      } else {
        // 如果分页信息为空，则设置为默认值
        params.size = this.pagination.defaultPageSize
        params.current = this.pagination.defaultCurrent
      }
      if (params.type === undefined) {
        delete params.type
      }
      this.$get('/cos/order-info/page', {
        ...params
      }).then((r) => {
        let data = r.data.data
        const pagination = {...this.pagination}
        pagination.total = data.total
        this.dataSource = data.records
        this.pagination = pagination
        // 数据加载完毕，关闭loading
      }).finally(() => {
        this.loading = false
      })
    }
  },
  watch: {}
}
</script>
<style lang="less" scoped>
@import "../../../../static/less/Common";
.order-cell {
  display: block;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.order-list /deep/ .ant-table-tbody > tr.order-row-pending > td { background-color: #e6f7ff; }
.order-list /deep/ .ant-table-tbody > tr.order-row-pending:hover > td { background-color: #bae7ff; }
.order-list /deep/ .ant-table-tbody > tr.order-row-completed > td { background-color: #f6ffed; }
.order-list /deep/ .ant-table-tbody > tr.order-row-completed:hover > td { background-color: #d9f7be; }
.order-list /deep/ .ant-table-tbody > tr.order-row-cancelled > td { background-color: #fff1f0; }
.order-list /deep/ .ant-table-tbody > tr.order-row-cancelled:hover > td { background-color: #ffccc7; }
.order-list /deep/ .ant-table-tbody > tr.order-row-unpaid > td { background-color: #fffbe6; }
.order-list /deep/ .ant-table-tbody > tr.order-row-unpaid:hover > td { background-color: #fff1b8; }
</style>
