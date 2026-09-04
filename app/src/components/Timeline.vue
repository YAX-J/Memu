<script setup lang="ts">
import { ref, onMounted } from "vue";

interface EventItem {
  id: string;
  type: string;
  snippet: string;
  score: number;
}

const events = ref<EventItem[]>([]);
const query = ref("");
const loading = ref(false);

async function search() {
  if (!query.value.trim()) return;
  loading.value = true;
  try {
    const r = await fetch(
      `http://127.0.0.1:8080/api/retrieve?q=${encodeURIComponent(query.value)}`
    );
    events.value = (await r.json()).results ?? [];
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  query.value = "最近";
  search();
});
</script>

<template>
  <section class="panel">
    <h2>时间线 / 检索</h2>
    <div class="search">
      <input v-model="query" @keyup.enter="search" placeholder="输入关键词，如：张三 版本评审" />
      <button class="primary" :disabled="loading" @click="search">搜索</button>
    </div>
    <ul class="list">
      <li v-for="e in events" :key="e.id">
        <span class="tag">{{ e.type }}</span>
        <span class="snip">{{ e.snippet }}</span>
      </li>
      <li v-if="!events.length" class="empty">无结果</li>
    </ul>
  </section>
</template>

<style scoped>
.panel {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 16px;
}
h2 {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 500;
}
.search {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
input {
  flex: 1;
  padding: 6px 10px;
  border: 1px solid var(--border);
  border-radius: 8px;
  font: inherit;
}
.list {
  list-style: none;
  padding: 0;
  margin: 0;
}
li {
  padding: 8px 0;
  border-bottom: 1px solid var(--border);
  display: flex;
  gap: 8px;
  align-items: baseline;
}
.tag {
  font-size: 11px;
  color: var(--accent);
  background: var(--accent-soft);
  padding: 1px 6px;
  border-radius: 4px;
}
.snip {
  color: var(--text);
  font-size: 13px;
}
.empty {
  color: var(--muted);
}
</style>
