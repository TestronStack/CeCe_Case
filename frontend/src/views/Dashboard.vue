<template>
  <div>
    <div class="page-head flex-between">
      <div>
        <h2>工作台</h2>
        <p>今日概览与生成趋势，数据均来自你的账号</p>
      </div>
      <el-button type="primary" @click="router.push('/generate/requirement')">
        <el-icon><Plus /></el-icon>新建生成任务
      </el-button>
    </div>

    <div class="stats">
      <div v-for="card in statCards" :key="card.label" class="stat panel">
        <div class="stat-icon" :style="{ background: card.bg, color: card.color }">
          <el-icon><component :is="card.icon" /></el-icon>
        </div>
        <div class="stat-body">
          <span>{{ card.label }}</span>
          <strong>{{ card.value }}<small v-if="card.unit">{{ card.unit }}</small></strong>
          <em>{{ card.hint }}</em>
        </div>
      </div>
    </div>

    <div class="grid-2">
      <div class="panel panel-pad">
        <div class="panel-title">
          <h3>近 7 天用例产出</h3>
          <span class="sub">按生成日期统计</span>
        </div>
        <div ref="trendRef" class="chart"></div>
      </div>
      <div class="panel panel-pad">
        <div class="panel-title">
          <h3>优先级分布</h3>
          <span class="sub">全部用例</span>
        </div>
        <div ref="priorityRef" class="chart"></div>
      </div>
    </div>

    <div class="grid-2 mt-20">
      <div class="panel panel-pad">
        <div class="panel-title">
          <h3>最近任务</h3>
          <el-button text type="primary" @click="router.push('/tasks')">全部记录</el-button>
        </div>
        <el-table :data="tasks" size="small" v-loading="loadingTasks"
                  :empty-text="'暂无任务，先去生成一批用例吧'">
          <el-table-column prop="title" label="任务" min-width="200" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="96">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" effect="light" size="small">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="160">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </div>
      <div class="panel panel-pad">
        <div class="panel-title">
          <h3>用例类型</h3>
          <span class="sub">UI / 接口</span>
        </div>
        <div ref="typeRef" class="chart small"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import http from '../api'

const router = useRouter()
const trendRef = ref()
const priorityRef = ref()
const typeRef = ref()
const stats = ref({})
const daily = ref([])
const tasks = ref([])
const loadingTasks = ref(false)
let charts = []

const statCards = computed(() => [
  { label: '用例总数', value: stats.value.caseCount ?? 0, unit: ' 条', hint: '账号下全部用例', icon: 'Files', bg: '#eff4ff', color: '#2563eb' },
  { label: '生成批次', value: stats.value.batchCount ?? 0, unit: ' 批', hint: `文档 ${stats.value.documentCount ?? 0} 份`, icon: 'Box', bg: '#ecfdf5', color: '#059669' },
  { label: '任务成功率', value: stats.value.successRate ?? 0, unit: '%', hint: `累计任务 ${stats.value.taskCount ?? 0} 次`, icon: 'DataLine', bg: '#fff7ed', color: '#ea580c' },
  { label: '评审通过率', value: stats.value.passRate ?? 0, unit: '%', hint: `待评审 ${stats.value.pendingReview ?? 0} 条`, icon: 'CircleCheck', bg: '#f5f3ff', color: '#7c3aed' }
])

function statusType(s) {
  return { SUCCESS: 'success', RUNNING: 'primary', PENDING: 'info', FAILED: 'danger' }[s] || 'info'
}

function statusText(s) {
  return { SUCCESS: '成功', RUNNING: '进行中', PENDING: '排队中', FAILED: '失败' }[s] || s
}

function formatTime(t) {
  if (!t) return '-'
  return t.replace('T', ' ').slice(0, 16)
}

function initCharts() {
  charts.forEach((c) => c.dispose())
  charts = []

  const trend = echarts.init(trendRef.value)
  trend.setOption({
    grid: { left: 8, right: 16, top: 24, bottom: 8, containLabel: true },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: daily.value.map((d) => d.date),
      axisLine: { lineStyle: { color: '#e6ebf5' } },
      axisLabel: { color: '#8b96b0' }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#f0f4fb' } },
      axisLabel: { color: '#8b96b0' }
    },
    series: [
      {
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        data: daily.value.map((d) => d.value),
        lineStyle: { width: 3, color: '#2563eb' },
        itemStyle: { color: '#2563eb', borderColor: '#fff', borderWidth: 2 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(37,99,235,0.22)' },
            { offset: 1, color: 'rgba(37,99,235,0.01)' }
          ])
        }
      }
    ]
  })
  charts.push(trend)

  const priorityData = (stats.value.priority || []).length
    ? stats.value.priority
    : [{ name: '暂无数据', value: 1, empty: true }]
  const priority = echarts.init(priorityRef.value)
  priority.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, icon: 'circle', textStyle: { color: '#5b6785' } },
    color: ['#2563eb', '#60a5fa', '#93c5fd', '#cbd5e1', '#e2e8f0'],
    series: [
      {
        type: 'pie',
        radius: ['52%', '74%'],
        center: ['50%', '44%'],
        itemStyle: { borderColor: '#fff', borderWidth: 3, borderRadius: 6 },
        label: { show: false },
        data: priorityData.map((d) => ({ ...d, value: d.empty ? 1 : d.value }))
      }
    ]
  })
  charts.push(priority)

  const typeData = (stats.value.typeDistribution || []).length
    ? stats.value.typeDistribution.map((d) => ({ ...d, name: d.name === 'UI' ? 'UI 用例' : '接口用例' }))
    : [{ name: '暂无数据', value: 1 }]
  const type = echarts.init(typeRef.value)
  type.setOption({
    grid: { left: 8, right: 20, top: 10, bottom: 8, containLabel: true },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: '#f0f4fb' } }, axisLabel: { color: '#8b96b0' } },
    yAxis: { type: 'category', data: typeData.map((d) => d.name), axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: '#5b6785' } },
    series: [
      {
        type: 'bar',
        barWidth: 18,
        data: typeData.map((d) => d.value),
        itemStyle: {
          borderRadius: [0, 8, 8, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#60a5fa' },
            { offset: 1, color: '#2563eb' }
          ])
        }
      }
    ]
  })
  charts.push(type)
}

async function load() {
  const res = await http.get('/dashboard/overview')
  stats.value = { ...res.data.stats, priority: res.data.priority, typeDistribution: res.data.typeDistribution }
  daily.value = res.data.daily || []
  await nextTick()
  initCharts()

  loadingTasks.value = true
  try {
    const t = await http.get('/tasks', { params: { limit: 6 } })
    tasks.value = t.data || []
  } finally {
    loadingTasks.value = false
  }
}

const onResize = () => charts.forEach((c) => c.resize())

onMounted(() => {
  load()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  charts.forEach((c) => c.dispose())
})
</script>

<style scoped>
.stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.stat {
  padding: 20px;
  display: flex;
  gap: 14px;
  align-items: flex-start;
  transition: transform 0.16s ease, box-shadow 0.16s ease;
}

.stat:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow);
}

.stat-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 21px;
  flex-shrink: 0;
}

.stat-body {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.stat-body span {
  font-size: 12.5px;
  color: var(--text-2);
}

.stat-body strong {
  font-size: 26px;
  font-weight: 650;
  letter-spacing: -0.4px;
  line-height: 1.2;
}

.stat-body small {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-3);
  margin-left: 2px;
}

.stat-body em {
  font-style: normal;
  font-size: 11.5px;
  color: var(--text-3);
}

.grid-2 {
  display: grid;
  grid-template-columns: 1.55fr 1fr;
  gap: 16px;
  margin-top: 16px;
}

.chart {
  height: 260px;
}

.chart.small {
  height: 220px;
}

@media (max-width: 1200px) {
  .stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .grid-2 {
    grid-template-columns: 1fr;
  }
}
</style>
