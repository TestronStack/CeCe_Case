import { defineStore } from 'pinia'
import http from '../api'

export const useProjectStore = defineStore('project', {
  state: () => ({
    projects: [],
    currentId: Number(localStorage.getItem('cece_project_id')) || null
  }),
  actions: {
    async load() {
      const res = await http.get('/projects')
      this.projects = res.data || []
      if (this.currentId && !this.projects.some((p) => p.id === this.currentId)) {
        this.currentId = null
        localStorage.removeItem('cece_project_id')
      }
    },
    setCurrent(id) {
      this.currentId = id
      if (id) localStorage.setItem('cece_project_id', String(id))
      else localStorage.removeItem('cece_project_id')
    }
  }
})
