<template>
  <div>
    <div class="page-head flex-between">
      <div>
        <h2>项目管理</h2>
        <p>按项目归档文档与用例，切换顶部项目即可只看该项目的数据</p>
      </div>
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>新建项目
      </el-button>
    </div>

    <div class="grid">
      <div v-for="p in projectStore.projects" :key="p.id" class="project-card panel">
        <div class="top">
          <div class="badge" :style="{ background: p.color + '18', color: p.color }">
            <el-icon><Grid /></el-icon>
          </div>
          <el-dropdown trigger="click" @command="(cmd) => onCommand(cmd, p)">
            <el-icon class="more"><MoreFilled /></el-icon>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="edit">编辑</el-dropdown-item>
                <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <h3>{{ p.name }}</h3>
        <p>{{ p.description || '暂无项目描述' }}</p>
        <div class="foot">
          <span>{{ formatTime(p.createdAt) }} 创建</span>
          <el-tag v-if="p.id === projectStore.currentId" size="small" effect="light">当前项目</el-tag>
        </div>
      </div>

      <div class="project-card add panel" @click="openCreate">
        <el-icon><Plus /></el-icon>
        <span>新建项目</span>
      </div>
    </div>

    <el-dialog v-model="dialog" :title="editing ? '编辑项目' : '新建项目'" width="460px">
      <el-form :model="form" label-position="top">
        <el-form-item label="项目名称">
          <el-input v-model="form.name" placeholder="如：电商 App 测试" />
        </el-form-item>
        <el-form-item label="项目描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="选填" />
        </el-form-item>
        <el-form-item label="标识色">
          <div class="colors">
            <span v-for="c in colors" :key="c" class="color" :class="{ active: form.color === c }"
                  :style="{ background: c }" @click="form.color = c"></span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api'
import { useProjectStore } from '../stores/project'

const projectStore = useProjectStore()
const dialog = ref(false)
const saving = ref(false)
const editing = ref(null)
const form = ref({ name: '', description: '', color: '#2563eb' })
const colors = ['#2563eb', '#059669', '#ea580c', '#7c3aed', '#0891b2', '#db2777']

function formatTime(t) {
  return t ? t.slice(0, 10) : '-'
}

function openCreate() {
  editing.value = null
  form.value = { name: '', description: '', color: '#2563eb' }
  dialog.value = true
}

function onCommand(cmd, p) {
  if (cmd === 'edit') {
    editing.value = p
    form.value = { name: p.name, description: p.description, color: p.color }
    dialog.value = true
  }
  if (cmd === 'delete') {
    ElMessageBox.confirm(`确定删除项目「${p.name}」？项目下的文档与用例不会被删除。`, '删除项目', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    }).then(async () => {
      await http.delete(`/projects/${p.id}`)
      ElMessage.success('已删除')
      await projectStore.load()
    }).catch(() => {})
  }
}

async function save() {
  if (!form.value.name.trim()) return ElMessage.warning('请输入项目名称')
  saving.value = true
  try {
    if (editing.value) {
      await http.put(`/projects/${editing.value.id}`, form.value)
    } else {
      await http.post('/projects', form.value)
    }
    ElMessage.success('已保存')
    dialog.value = false
    await projectStore.load()
  } finally {
    saving.value = false
  }
}

onMounted(() => projectStore.load())
</script>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.project-card {
  padding: 20px;
  display: flex;
  flex-direction: column;
  min-height: 168px;
  transition: transform 0.16s ease, box-shadow 0.16s ease;
}

.project-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow);
}

.top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.badge {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 19px;
}

.more {
  color: var(--text-3);
  cursor: pointer;
}

.project-card h3 {
  margin: 16px 0 6px;
  font-size: 15.5px;
  font-weight: 600;
}

.project-card p {
  margin: 0;
  color: var(--text-2);
  font-size: 12.5px;
  line-height: 1.6;
  flex: 1;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.foot {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--border);
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11.5px;
  color: var(--text-3);
}

.add {
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-style: dashed;
  background: #fbfdff;
  color: var(--text-3);
  cursor: pointer;
  font-size: 13px;
}

.add:hover {
  color: var(--brand);
  border-color: var(--brand);
}

.add .el-icon {
  font-size: 20px;
}

.colors {
  display: flex;
  gap: 10px;
}

.color {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  cursor: pointer;
  border: 2px solid transparent;
  transition: transform 0.12s ease;
}

.color.active {
  border-color: #16213d;
  transform: scale(1.08);
}
</style>
