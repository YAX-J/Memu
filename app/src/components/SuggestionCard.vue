<script setup lang="ts">
import { ref, onMounted } from "vue";
import { refreshSuggestions, sendFeedback, type Suggestion } from "../api/client";

const list = ref<Suggestion[]>([]);
const coldStart = ref(false);
const loading = ref(false);
const error = ref("");

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const r = await refreshSuggestions();
    list.value = r.suggestions;
    coldStart.value = r.coldStart;
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    loading.value = false;
  }
}

async function act(id: string, action: "accepted" | "snoozed" | "dismissed") {
  await sendFeedback(id, action);
  list.value = list.value.filter((s) => s.id !== id);
}

onMounted(load);
</script>

<template>
  <section class="panel">
    <header>
      <h2>主动建议</h2>
      <button class="primary" :disabled="loading" @click="load">
        {{ loading ? "刷新中…" : "刷新" }}
      </button>
    </header>

    <p v-if="coldStart" class="hint">
      冷启动期：数据不足，暂不主动推送。继续使用几天后会开始出现建议。
    </p>
    <p v-else-if="!list.length && !error" class="hint">暂无建议。</p>
    <p v-if="error" class="err">后端连接失败：{{ error }}</p>

    <article v-for="s in list" :key="s.id" class="card">
      <div class="card-head">
        <span class="title">{{ s.title }}</span>
        <span class="conf">{{ (s.confidence * 100).toFixed(0) }}%</span>
      </div>
      <p class="reason">{{ s.reason }}</p>
      <div class="actions">
        <button @click="act(s.id, 'accepted')">采纳</button>
        <button @click="act(s.id, 'snoozed')">稍后</button>
        <button class="dismiss" @click="act(s.id, 'dismissed')">忽略</button>
      </div>
    </article>
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
.hint,
.err {
  color: var(--muted);
  font-size: 13px;
}
.err {
  color: var(--danger);
}
.card {
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 12px;
  margin-top: 12px;
}
.card-head {
  display: flex;
  justify-content: space-between;
}
.title {
  font-weight: 500;
}
.conf {
  color: var(--accent);
  font-size: 12px;
}
.reason {
  color: var(--muted);
  font-size: 13px;
  margin: 6px 0 10px;
}
.actions {
  display: flex;
  gap: 8px;
}
.dismiss {
  color: var(--danger);
  border-color: var(--danger);
}
</style>
