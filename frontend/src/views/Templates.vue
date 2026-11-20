<template>
  <div>
    <div class="page-head flex-between">
      <div>
        <h2>模板管理</h2>
        <p>标准模板开箱即用，也可以按自己的用例格式自定义列</p>
      </div>
      <el-button type="primary" @click="dialog = true">
        <el-icon><Plus /></el-icon>新建自定义模板
      </el-button>
    </div>

    <div class="section">
      <h3 class="section-title">内置标准模板</h3>
      <div class="grid">
        <div v-for="b in builtins" :key="b.code" class="tpl-card panel">
          <div class="tpl-head">
            <div class="tpl-icon"><el-icon><Collection /></el-icon></div>
            <div>
              <strong>{{ b.name }}</strong>
              <span>{{ b.columns.length }} 列</span>
            </div>
            <el-tag size="small" effect="light" type="primary">内置</el-tag>
          </div>
          <p>{{ b.description }}</p>
          <div class="cols">
            <span v-for="c in b.columns.slice(0, 8)" :key="c.fieldName" class="col">{{ c.displayName }}</span>
            <span v-if="b.columns.length > 8" class="col more">+{{ b.columns.length - 8 }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="section mt-20">
      <h3 class="section-title">我的模板</h3>
      <div v-if="mine.length" class="grid">
        <div v-for="t in mine" :key="t.id" class="tpl-card panel">
          <div class="tpl-head">
            <div class="tpl-icon custom"><el-icon><Star /></el-icon></div>
            <div>
              <strong>{{ t.name }}</strong>
              <span>{{ t.columnsText.split('|').length }} 列</span>
            </div>
            <el-popconfirm title="删除该模板？" @confirm="remove(t)">
              <template #reference>
                <el-button text type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </div>
          <div class="cols">
            <span v-for="c in t.columnsText.split('|').slice(0, 8)" :key="c" class="col">{{ c }}</span>
            <span v-if="t.columnsText.split('|').length > 8" class="col more">
              +{{ t.columnsText.split('|').length - 8 }}
            </span>
          </div>
          <div class="tpl-foot">{{ formatTime(t.createdAt) }}</div>
        </div>
      </div>
      <div v-else class="panel empty-tip">还没有自定义模板，点击右上角创建</div>
    </div>

    <el-dialog v-model="dialog" title="新建自定义模板" width="520px">
      <el-form :model="form" label-position="top">
        <el-form-item label="模板名称">
          <el-input v-model="form.name" placeholder="如：公司用例模板" />
        </el-form-item>
        <el-form-item label="列名（用 | 分隔）">
          <el-input v-model="form.columns" type="textarea" :rows="5"
                    placeholder="用例编号|模块|用例标题|前置条件|操作步骤|预期结果|优先级" />
        </el-form-item>
        <div class="preview">
          <span v-for="c in previewCols" :key="c" class="col">{{ c }}</span>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存模板</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api'

const builtins = ref([])
const mine = ref([])
const dialog = ref(false)
const saving = ref(false)
const form = ref({ name: '', columns: '' })

const previewCols = computed(() =>
  (form.value.columns || '').split('|').map((s) => s.trim()).filter(Boolean))

function formatTime(t) {
  return t ? t.replace('T', ' ').slice(0, 16) : ''
}

async function load() {
  const res = await http.get('/templates')
  builtins.value = res.data.builtins || []
  mine.value = res.data.mine || []
}

async function save() {
  if (!form.value.name.trim() || !form.value.columns.trim()) {
    return ElMessage.warning('请填写模板名称和列名')
  }
  saving.value = true
  try {
    await http.post('/templates', form.value)
    ElMessage.success('模板已保存')
    dialog.value = false
    form.value = { name: '', columns: '' }
    load()
  } finally {
    saving.value = false
  }
}

async function remove(t) {
  await http.delete(`/templates/${t.id}`)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.section-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 12px;
  color: var(--text-2);
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.tpl-card {
  padding: 18px 20px;
}

.tpl-head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.tpl-head > div:nth-child(2) {
  display: flex;
  flex-direction: column;
  flex: 1;
}

.tpl-head strong {
  font-size: 14px;
}

.tpl-head span {
  font-size: 11.5px;
  color: var(--text-3);
}

.tpl-icon {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  background: #eff4ff;
  color: #2563eb;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

.tpl-icon.custom {
  background: #f5f3ff;
  color: #7c3aed;
}

.tpl-card p {
  margin: 12px 0;
  font-size: 12.5px;
  color: var(--text-2);
  line-height: 1.6;
}

.cols {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.col {
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 6px;
  background: #f4f7fd;
  color: #5b6785;
}

.col.more {
  background: #eff4ff;
  color: #2563eb;
}

.tpl-foot {
  margin-top: 14px;
  padding-top: 10px;
  border-top: 1px dashed var(--border);
  font-size: 11.5px;
  color: var(--text-3);
}

.preview {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}
</style>
