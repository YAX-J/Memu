<script setup lang="ts">
import { ref, onMounted } from "vue";
import { fetchServiceStatus, restartService, type ServiceStatus } from "../api/client";

const statuses = ref<ServiceStatus[]>([]);
const error = ref("");

async function load() {
  error.value = "";
  try {
    statuses.value = await fetchServiceStatus();
  } catch (e) {
    error.value = (e as Error).message;
  }
}

async function restart(name: string) {
  try {
    await restartService(name);
    await load();
  } catch (e) {
    error.value = (e as Error).message;
  }
}

onMounted(load);
</script>

<template>
  <section class="panel">
    <header>
      <h2>进程状态</h2>
      <button @click="load">刷新</button>
    </header>
    <p v-if="error" class="err">{{ error }}（非 Tauri 运行时下不可用）</p>
    <table v-else>
      <thead>
        <tr><th>服务</th><th>状态</th><th>操作</th></tr>
      </thead>
      <tbody>
        <tr v-for="s in statuses" :key="s.name">
          <td>{{ s.name }}</td>
          <td :class="s.alive ? 'on' : 'off'">{{ s.alive ? "运行中" : "已停止" }}</td>
          <td><button @click="restart(s.name)">重启</button></td>
        </tr>
        <tr v-if="!statuses.length"><td colspan="3" class="hint">无数据（纯浏览器调试时不可用）</td></tr>
      </tbody>
    </table>
  </section>
</template>

<style scoped>
.panel {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 16px;
}
header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 500;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th,
td {
  text-align: left;
  padding: 8px 4px;
  border-bottom: 1px solid var(--border);
  font-size: 13px;
}
.on {
  color: #1d9e75;
}
.off {
  color: var(--danger);
}
.hint,
.err {
  color: var(--muted);
}
.err {
  color: var(--danger);
}
</style>
