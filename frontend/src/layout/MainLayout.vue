<template>
  <div class="layout">
    <aside class="sidebar" :class="{ collapsed }">
      <div class="brand" @click="router.push('/dashboard')">
        <div class="logo">
          <el-icon><MagicStick /></el-icon>
        </div>
        <div v-show="!collapsed" class="brand-text">
          <strong>测测 CeCe</strong>
          <span>AI TEST PLATFORM</span>
        </div>
      </div>

      <nav class="menu">
        <template v-for="group in menus" :key="group.title">
          <div v-show="!collapsed" class="menu-group">{{ group.title }}</div>
          <div
            v-for="item in group.items"
            :key="item.path"
            class="menu-item"
            :class="{ active: isActive(item) }"
            @click="router.push(item.path)"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span v-show="!collapsed">{{ item.label }}</span>
            <el-tag v-if="item.badge && !collapsed" size="small" type="primary" effect="light" round>
              {{ item.badge }}
            </el-tag>
          </div>
        </template>
      </nav>

      <div class="sidebar-foot" @click="collapsed = !collapsed">
        <el-icon><component :is="collapsed ? 'Expand' : 'Fold'" /></el-icon>
        <span v-show="!collapsed">收起菜单</span>
      </div>
    </aside>

    <div class="main">
      <header class="topbar">
        <div class="crumb">
          <span class="crumb-root">CeCe</span>
          <el-icon><ArrowRight /></el-icon>
          <span>{{ route.meta.title || '工作台' }}</span>
        </div>
        <div class="topbar-right">
          <el-select v-model="projectId" placeholder="全部项目" clearable size="default"
                     style="width: 168px" @change="switchProject">
            <el-option v-for="p in projectStore.projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-tooltip content="任务记录">
            <el-button circle @click="router.push('/tasks')">
              <el-icon><Tickets /></el-icon>
            </el-button>
          </el-tooltip>
          <el-dropdown trigger="click" @command="onCommand">
            <div class="user-chip">
              <div class="avatar">{{ initials }}</div>
              <div class="user-meta">
                <strong>{{ user.nickname || user.username }}</strong>
                <span>测试工程师</span>
              </div>
              <el-icon><CaretBottom /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="settings">
                  <el-icon><Setting /></el-icon>个人设置
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useProjectStore } from '../stores/project'

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()
const collapsed = ref(false)
const projectId = ref(projectStore.currentId)

const user = computed(() => JSON.parse(localStorage.getItem('cece_user') || '{}'))
const initials = computed(() => (user.value.nickname || user.value.username || 'U').slice(0, 1).toUpperCase())

const menus = [
  {
    title: '总览',
    items: [{ path: '/dashboard', label: '工作台', icon: 'Odometer' }]
  },
  {
    title: 'AI 生成',
    items: [
      { path: '/generate/requirement', label: '需求文档生成', icon: 'Document' },
      { path: '/generate/api', label: '接口用例生成', icon: 'Connection' },
      { path: '/generate/script', label: '自动化脚本', icon: 'Cpu' },
      { path: '/generate/text', label: '自然语言生成', icon: 'ChatDotRound' }
    ]
  },
  {
    title: '资产',
    items: [
      { path: '/cases', label: '用例库', icon: 'Files' },
      { path: '/documents', label: '文档库', icon: 'FolderOpened' },
      { path: '/tasks', label: '任务记录', icon: 'Tickets' },
      { path: '/projects', label: '项目管理', icon: 'Grid' }
    ]
  },
  {
    title: '配置',
    items: [
      { path: '/templates', label: '模板管理', icon: 'Collection' },
      { path: '/settings', label: '设置', icon: 'Setting' }
    ]
  }
]

function isActive(item) {
  if (item.path === '/cases') return route.path.startsWith('/cases')
  return route.path === item.path
}

function switchProject(val) {
  projectStore.setCurrent(val || null)
  window.location.reload()
}

function onCommand(cmd) {
  if (cmd === 'settings') router.push('/settings')
  if (cmd === 'logout') {
    localStorage.removeItem('cece_token')
    localStorage.removeItem('cece_user')
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}

onMounted(() => projectStore.load())
</script>

<style scoped>
.layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.sidebar {
  width: 236px;
  flex-shrink: 0;
  background: #fff;
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  transition: width 0.2s ease;
}

.sidebar.collapsed {
  width: 76px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 20px 16px;
  cursor: pointer;
}

.logo {
  width: 38px;
  height: 38px;
  border-radius: 11px;
  background: linear-gradient(135deg, #3b82f6, #1d4ed8);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  box-shadow: 0 8px 18px rgba(37, 99, 235, 0.28);
  flex-shrink: 0;
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.brand-text strong {
  font-size: 16px;
  letter-spacing: 0.3px;
}

.brand-text span {
  font-size: 10px;
  color: var(--text-3);
  letter-spacing: 1.2px;
}

.menu {
  flex: 1;
  overflow-y: auto;
  padding: 4px 12px 12px;
}

.menu-group {
  font-size: 11px;
  color: #a6b0c6;
  letter-spacing: 1px;
  padding: 16px 12px 8px;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  margin-bottom: 2px;
  border-radius: 10px;
  color: #4b5670;
  font-size: 13.5px;
  cursor: pointer;
  position: relative;
  transition: all 0.16s ease;
  white-space: nowrap;
}

.menu-item:hover {
  background: #f4f7fd;
  color: var(--brand);
}

.menu-item.active {
  background: var(--brand-light);
  color: var(--brand);
  font-weight: 600;
}

.menu-item.active::before {
  content: '';
  position: absolute;
  left: -12px;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 18px;
  border-radius: 0 3px 3px 0;
  background: var(--brand);
}

.menu-item .el-icon {
  font-size: 17px;
}

.menu-item .el-tag {
  margin-left: auto;
}

.sidebar-foot {
  border-top: 1px solid var(--border);
  padding: 14px 22px;
  color: var(--text-3);
  font-size: 12.5px;
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.sidebar-foot:hover {
  color: var(--brand);
}

.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.topbar {
  height: 64px;
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}

.crumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
}

.crumb-root {
  color: var(--text-3);
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 10px 4px 4px;
  border-radius: 999px;
  cursor: pointer;
  transition: background 0.16s ease;
  flex-shrink: 0;
}

.user-chip:hover {
  background: #f4f7fd;
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: linear-gradient(135deg, #60a5fa, #2563eb);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 14px;
}

.user-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
  white-space: nowrap;
}

.user-meta strong {
  font-size: 13px;
  font-weight: 600;
}

.user-meta span {
  font-size: 11px;
  color: var(--text-3);
}

.content {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 24px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.fade-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.fade-leave-to {
  opacity: 0;
}

/* 窄屏：隐藏面包屑与用户副标题，避免顶栏拥挤 */
@media (max-width: 900px) {
  .crumb {
    display: none;
  }

  .user-meta span {
    display: none;
  }
}
</style>
