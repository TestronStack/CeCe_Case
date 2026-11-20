<template>
  <div>
    <div class="page-head flex-between">
      <div>
        <h2>任务记录</h2>
        <p>所有生成任务的状态、耗时与结果，可随时回到对应批次</p>
      </div>
      <el-button :icon="'Refresh'" @click="load">刷新</el-button>
    </div>

    <div class="panel panel-pad">
      <div class="toolbar">
        <el-select v-model="status" clearable placeholder="全部状态" style="width: 150px" @change="load">
          <el-option label="排队中" value="PENDING" />
          <el-option label="生成中" value="RUNNING" />
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
        </el-select>
        <span class="muted">共 {{ tasks.length }} 条</span>
      </div>

      <el-table :data="tasks" v-loading="loading" empty-text="暂无任务记录">
        <el-table-column prop="title" label="任务" min-width="240" show-overflow-tooltip />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <span class="muted">{{ typeText(row.taskType) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small" effect="light">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="160">
          <template #default="{ row }">
            <el-progress :percentage="row.progress || 0" :stroke-width="6"
                         :status="row.status === 'FAILED' ? 'exception' : row.status === 'SUCCESS' ? 'success' : ''" />
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="100">
          <template #default="{ row }">
            {{ row.durationMs ? (row.durationMs / 1000).toFixed(0) + 's' : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" align="right">
          <template #default="{ row }">
            <el-button v-if="row.batchId" text type="primary" @click="router.push(`/cases/${row.batchId}`)">
              查看用例
            </el-button>
            <el-tooltip v-else-if="row.message" :content="row.message" placement="top">
              <el-button text>失败原因</el-button>
            </el-tooltip>
            <el-popconfirm title="删除该条记录？" @confirm="remove(row)">
              <template #reference>
                <el-button text type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../api'

const router = useRouter()
const tasks = ref([])
const loading = ref(false)
const status = ref('')
let timer = null

function formatTime(t) {
  return t ? t.replace('T', ' ').slice(0, 16) : '-'
}

function statusType(s) {
  return { SUCCESS: 'success', RUNNING: 'primary', PENDING: 'info', FAILED: 'danger' }[s] || 'info'
}

function statusText(s) {
  return { SUCCESS: '成功', RUNNING: '生成中', PENDING: '排队中', FAILED: '失败' }[s] || s
}

function typeText(t) {
  return {
    REQUIREMENT_UI: '需求文档',
    API_CASE: '接口用例',
    API_SCRIPT: '自动化脚本',
    TEXT: '自然语言'
  }[t] || t
}

async function load() {
  loading.value = true
  try {
    const res = await http.get('/tasks', {
      params: { status: status.value || undefined, limit: 100 }
    })
    tasks.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function remove(row) {
  await http.delete(`/tasks/${row.id}`)
  ElMessage.success('已删除')
  load()
}

onMounted(() => {
  load()
  timer = setInterval(() => {
    if (tasks.value.some((t) => t.status === 'RUNNING' || t.status === 'PENDING')) load()
  }, 5000)
})

onBeforeUnmount(() => clearInterval(timer))
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
</style>
