<template>
  <div>
    <div class="page-head">
      <h2>需求文档生成</h2>
      <p>上传需求文档，按章节或关键词精准生成标准 15 列 UI 功能测试用例</p>
    </div>

    <div class="two-col">
      <div class="panel panel-pad">
        <div class="panel-title"><h3>1. 选择文档</h3></div>
        <el-form label-position="top">
          <DocSource v-model="documentId" v-model:projectId="projectId" @uploaded="onDoc" />

          <el-form-item label="读取范围">
            <el-radio-group v-model="readRangeType" @change="onRange">
              <el-radio-button value="all">全部读取</el-radio-button>
              <el-radio-button value="page">指定页数</el-radio-button>
              <el-radio-button value="chapter">指定章节</el-radio-button>
              <el-radio-button value="keyword">指定关键词</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item v-if="readRangeType === 'page'" label="页码范围">
            <div class="flex gap-8" style="width: 100%">
              <el-input-number v-model="startPage" :min="1" :max="999" controls-position="right" />
              <span class="muted" style="line-height: 32px">至</span>
              <el-input-number v-model="endPage" :min="1" :max="999" controls-position="right" />
            </div>
          </el-form-item>

          <el-form-item v-if="readRangeType === 'chapter'" label="选择章节（可多选）">
            <el-select v-model="chapters" multiple filterable collapse-tags collapse-tags-tooltip
                       placeholder="先选择文档，再勾选章节" style="width: 100%" :loading="loadingChapters">
              <el-option v-for="c in chapterOptions" :key="c" :label="c" :value="c" />
            </el-select>
          </el-form-item>

          <el-form-item v-if="readRangeType === 'keyword'" label="关键词">
            <el-input v-model="keywords" placeholder="多个关键词用逗号分隔，如：登录,支付" />
          </el-form-item>
        </el-form>
      </div>

      <div class="panel panel-pad">
        <div class="panel-title"><h3>2. 生成参数</h3></div>
        <el-form label-position="top">
          <el-form-item label="创建人">
            <el-input v-model="creator" placeholder="将写入用例的「创建人」列" />
          </el-form-item>
          <el-form-item label="关联需求">
            <el-input v-model="relatedRequirement" placeholder="如：REQ-001 用户登录优化" />
          </el-form-item>
          <el-form-item label="导出格式">
            <el-select v-model="outputFormat" style="width: 100%">
              <el-option label="Excel (.xlsx)" value="xlsx" />
              <el-option label="CSV (.csv)" value="csv" />
              <el-option label="JSON (.json)" value="json" />
              <el-option label="Markdown (.md)" value="md" />
            </el-select>
          </el-form-item>
          <el-form-item label="自定义列（可选）">
            <el-input v-model="customTemplate" type="textarea" :rows="3"
                      placeholder="用 | 分隔列名，留空使用标准 15 列模板" />
          </el-form-item>

          <el-button type="primary" size="large" class="gen-btn" :loading="submitting"
                     :disabled="!documentId" @click="submit">
            <el-icon><MagicStick /></el-icon>开始生成用例
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
import { onMounted, ref } from 'vue'
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
const chapters = ref([])
const chapterOptions = ref([])
const loadingChapters = ref(false)
const keywords = ref('')
const creator = ref('')
const relatedRequirement = ref('')
const outputFormat = ref('xlsx')
const customTemplate = ref('')
const submitting = ref(false)
const task = ref(null)

onMounted(() => {
  const user = JSON.parse(localStorage.getItem('cece_user') || '{}')
  creator.value = user.nickname || user.username || ''
})

async function onDoc(doc) {
  if (!doc) return
  chapterOptions.value = []
  chapters.value = []
  if (readRangeType.value === 'chapter') loadChapters()
}

async function loadChapters() {
  if (!documentId.value) return
  loadingChapters.value = true
  try {
    const fd = new FormData()
    fd.append('documentId', documentId.value)
    const res = await http.post('/generation/chapters', fd)
    chapterOptions.value = res.data.chapters || []
    if (!chapterOptions.value.length) ElMessage.info('该文档未识别到章节标题，可使用全部读取')
  } finally {
    loadingChapters.value = false
  }
}

function onRange(val) {
  if (val === 'chapter' && documentId.value && !chapterOptions.value.length) loadChapters()
}

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
    if (chapters.value.length) fd.append('chapterTitles', chapters.value.join(','))
    if (keywords.value) fd.append('keywords', keywords.value)
    if (creator.value) fd.append('creator', creator.value)
    if (relatedRequirement.value) fd.append('relatedRequirement', relatedRequirement.value)
    if (customTemplate.value) fd.append('customTemplate', customTemplate.value)
    fd.append('outputFormat', outputFormat.value)

    const res = await http.post('/generation/requirement', fd)
    task.value = res.data
    ElMessage.success('任务已提交，正在生成')
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

@media (max-width: 1100px) {
  .two-col {
    grid-template-columns: 1fr;
  }
}
</style>
