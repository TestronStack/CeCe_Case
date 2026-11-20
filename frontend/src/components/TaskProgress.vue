<template>
  <div v-if="task" class="progress-panel">
    <div class="head">
      <div class="left">
        <el-tag :type="tagType" effect="light" round>{{ statusText }}</el-tag>
        <span class="title">{{ task.title }}</span>
      </div>
      <span class="timer">{{ elapsed }}</span>
    </div>

    <el-progress :percentage="task.progress || 0" :status="progressStatus"
                 :stroke-width="10" :show-text="false" class="bar" />

    <div class="stage">
      <span>{{ task.stage || '等待调度' }}</span>
      <span>{{ task.progress || 0 }}%</span>
    </div>

    <div v-if="task.status === 'FAILED'" class="error">
      <el-icon><WarningFilled /></el-icon>{{ task.message }}
    </div>

    <div v-if="task.status === 'SUCCESS'" class="success">
      <div class="ok-line">
        <el-icon><CircleCheckFilled /></el-icon>
        <span>{{ task.message }}，用时 {{ seconds(task.durationMs) }}</span>
      </div>
      <div class="actions">
        <el-button type="primary" @click="$emit('view', task)">
          <el-icon><View /></el-icon>查看用例
        </el-button>
        <el-button @click="download">
          <el-icon><Download /></el-icon>下载文件
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { downloadBlob } from '../api'

const props = defineProps({ task: { type: Object, default: null } })
const emit = defineEmits(['update', 'view'])

let es = null
let ticker = null
const elapsedSec = ref(0)

const statusText = computed(() => ({
  PENDING: '排队中', RUNNING: '生成中', SUCCESS: '已完成', FAILED: '失败'
}[props.task?.status] || ''))

const tagType = computed(() => ({
  PENDING: 'info', RUNNING: 'primary', SUCCESS: 'success', FAILED: 'danger'
}[props.task?.status] || 'info'))

const progressStatus = computed(() => {
  if (props.task?.status === 'SUCCESS') return 'success'
  if (props.task?.status === 'FAILED') return 'exception'
  return ''
})

const elapsed = computed(() => {
  if (props.task?.durationMs) return `耗时 ${seconds(props.task.durationMs)}`
  return `已用 ${elapsedSec.value}s`
})

function seconds(ms) {
  if (!ms) return '0s'
  return ms > 60000 ? `${(ms / 60000).toFixed(1)} 分钟` : `${Math.round(ms / 1000)}s`
}

function stop() {
  if (es) {
    es.close()
    es = null
  }
  if (ticker) {
    clearInterval(ticker)
    ticker = null
  }
}

function start(id) {
  stop()
  elapsedSec.value = 0
  ticker = setInterval(() => elapsedSec.value++, 1000)
  const token = localStorage.getItem('cece_token')
  es = new EventSource(`/api/tasks/${id}/events?token=${encodeURIComponent(token)}`)
  es.addEventListener('progress', (e) => {
    const data = JSON.parse(e.data)
    emit('update', data)
    if (data.status === 'SUCCESS' || data.status === 'FAILED') stop()
  })
  es.onerror = () => stop()
}

function download() {
  if (!props.task?.batchId) return
  downloadBlob(`/api/batches/${props.task.batchId}/download`, props.task.title + '.xlsx')
}

watch(() => props.task?.id, (id) => {
  if (id) start(id)
})

onBeforeUnmount(stop)
</script>

<style scoped>
.progress-panel {
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 18px 20px;
  background: #fbfdff;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.title {
  font-size: 13.5px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.timer {
  font-size: 12px;
  color: var(--text-3);
  flex-shrink: 0;
}

.bar {
  margin-bottom: 8px;
}

.stage {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-2);
}

.error {
  margin-top: 14px;
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 10px 12px;
  border-radius: 9px;
  background: #fef2f2;
  color: #b91c1c;
  font-size: 12.5px;
}

.success {
  margin-top: 16px;
}

.ok-line {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #047857;
  font-size: 12.5px;
  margin-bottom: 12px;
}

.actions {
  display: flex;
  gap: 10px;
}
</style>
