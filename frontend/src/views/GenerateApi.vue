<template>
  <div>
    <div class="page-head">
      <h2>接口用例生成</h2>
      <p>识别接口文档中的接口定义，生成包含请求参数、断言规则与业务码的 24 列接口用例</p>
    </div>

    <div class="two-col">
      <div class="panel panel-pad">
        <div class="panel-title"><h3>1. 选择接口文档</h3></div>
        <el-form label-position="top">
          <DocSource v-model="documentId" v-model:projectId="projectId"
                     accept=".docx,.pdf,.json,.yaml,.yml,.md"
                     accept-text="支持 DOCX / PDF / JSON / YAML / MD（Swagger 或接口说明）" />

          <el-form-item label="读取范围">
            <el-radio-group v-model="readRangeType">
              <el-radio-button value="all">全部读取</el-radio-button>
              <el-radio-button value="page">指定页数</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item v-if="readRangeType === 'page'" label="页码范围">
            <div class="flex gap-8" style="width: 100%">
              <el-input-number v-model="startPage" :min="1" :max="999" controls-position="right" />
              <span class="muted" style="line-height: 32px">至</span>
              <el-input-number v-model="endPage" :min="1" :max="999" controls-position="right" />
            </div>
          </el-form-item>
        </el-form>

        <div class="hint">
          <el-icon><InfoFilled /></el-icon>
          <span>每个接口最多生成 8 条用例，整份文档按预算裁剪，优先保留 P0 / P1。</span>
        </div>
      </div>

      <div class="panel panel-pad">
        <div class="panel-title"><h3>2. 导出设置</h3></div>
        <el-form label-position="top">
          <el-form-item label="导出格式">
            <el-select v-model="outputFormat" style="width: 100%">
              <el-option label="Excel (.xlsx)" value="xlsx" />
              <el-option label="CSV (.csv)" value="csv" />
              <el-option label="JSON (.json)" value="json" />
              <el-option label="Markdown (.md)" value="md" />
            </el-select>
          </el-form-item>
          <el-form-item label="说明">
            <div class="desc-box">
              生成的用例会保存到用例库，可在线编辑、评审与二次导出；
              如需同时产出自动化脚本，请使用「自动化脚本」功能。
            </div>
          </el-form-item>
          <el-button type="primary" size="large" class="gen-btn" :loading="submitting"
                     :disabled="!documentId" @click="submit">
            <el-icon><Connection /></el-icon>开始生成接口用例
          </el-button>
        </el-form>
      </div>
    </div>

    <div v-if="task" class="panel panel-pad mt-20">
      <div class="panel-title"><h3>生成进度</h3><span class="sub">实时推送</span></div>
      <TaskProgress :task="task" @update="onUpdate" @view="viewCases" />
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../api'
import DocSource from '../components/DocSource.vue'
import TaskProgress from '../components/TaskProgress.vue'
import { useProjectStore } from '../stores/project'

const router = useRouter()
const projectStore = useProjectStore()

const documentId = ref(null)
const projectId = ref(projectStore.currentId)
const readRangeType = ref('all')
const startPage = ref(1)
const endPage = ref(20)
const outputFormat = ref('xlsx')
const submitting = ref(false)
const task = ref(null)

async function submit() {
  if (!documentId.value) return ElMessage.warning('请先上传或选择文档')
  submitting.value = true
  try {
    const fd = new FormData()
    fd.append('documentId', documentId.value)
    if (projectId.value) fd.append('projectId', projectId.value)
    fd.append('readRangeType', readRangeType.value)
    fd.append('startPage', startPage.value)
    fd.append('endPage', endPage.value)
    fd.append('outputFormat', outputFormat.value)
    const res = await http.post('/generation/api', fd)
    task.value = res.data
    ElMessage.success('任务已提交，正在识别接口')
  } finally {
    submitting.value = false
  }
}

function onUpdate(data) {
  task.value = { ...task.value, ...data }
}

function viewCases(t) {
  router.push(`/cases/${t.batchId}`)
}
</script>

<style scoped>
.two-col {
  display: grid;
  grid-template-columns: 1.35fr 1fr;
  gap: 16px;
}

.gen-btn {
  width: 100%;
  margin-top: 8px;
  height: 44px;
}

.hint {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  margin-top: 6px;
  padding: 10px 12px;
  border-radius: 9px;
  background: var(--brand-light);
  color: #3b5aa8;
  font-size: 12px;
  line-height: 1.6;
}

.desc-box {
  width: 100%;
  padding: 14px;
  border-radius: 10px;
  background: #f7f9fd;
  color: var(--text-2);
  font-size: 12.5px;
  line-height: 1.8;
}

@media (max-width: 1100px) {
  .two-col {
    grid-template-columns: 1fr;
  }
}
</style>
