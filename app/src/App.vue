<script setup lang="ts">
import { ref } from "vue";
import SuggestionCard from "./components/SuggestionCard.vue";
import Timeline from "./components/Timeline.vue";
import GraphView from "./components/GraphView.vue";
import SettingsView from "./components/SettingsView.vue";

type Tab = "suggestions" | "timeline" | "graph" | "settings";
const tab = ref<Tab>("suggestions");
const tabs: { key: Tab; label: string }[] = [
  { key: "suggestions", label: "建议" },
  { key: "timeline", label: "时间线" },
  { key: "graph", label: "图谱" },
  { key: "settings", label: "设置" },
];
</script>

<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="logo">Memu</div>
      <nav>
        <button
          v-for="t in tabs"
          :key="t.key"
          :class="{ active: tab === t.key }"
          @click="tab = t.key"
        >
          {{ t.label }}
        </button>
      </nav>
      <footer class="ver">v0.1.0</footer>
    </aside>
    <main class="content">
      <SuggestionCard v-if="tab === 'suggestions'" />
      <Timeline v-else-if="tab === 'timeline'" />
      <GraphView v-else-if="tab === 'graph'" />
      <SettingsView v-else />
    </main>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  height: 100%;
}
.sidebar {
  width: 180px;
  background: var(--panel);
  border-right: 1px solid var(--border);
  padding: 16px 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.logo {
  font-weight: 600;
  font-size: 18px;
  padding: 4px 8px;
  color: var(--accent);
}
nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
nav button {
  text-align: left;
  border: none;
  background: transparent;
  padding: 8px 10px;
  border-radius: 8px;
  color: var(--muted);
}
nav button:hover {
  background: var(--accent-soft);
  color: var(--accent);
  border: none;
}
nav button.active {
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 500;
}
.ver {
  margin-top: auto;
  font-size: 11px;
  color: var(--muted);
  padding: 8px;
}
.content {
  flex: 1;
  padding: 20px;
  overflow: auto;
}
</style>
