<script setup lang="ts">
import { ref, onMounted } from "vue";
import { fetchSubgraph, type GraphNode, type GraphEdge } from "../api/client";

const nodes = ref<GraphNode[]>([]);
const edges = ref<GraphEdge[]>([]);
const error = ref("");

async function load() {
  error.value = "";
  try {
    const g = await fetchSubgraph();
    nodes.value = g.nodes;
    edges.value = g.edges;
  } catch (e) {
    error.value = (e as Error).message;
  }
}

onMounted(load);
</script>

<template>
  <section class="panel">
    <header>
      <h2>知识图谱</h2>
      <button @click="load">刷新</button>
    </header>
    <p v-if="error" class="err">加载失败：{{ error }}</p>
    <div v-else class="canvas">
      <p class="placeholder">
        P0 占位：共 {{ nodes.length }} 节点 / {{ edges.length }} 边。<br />
        P2 接入力导向图（如 d3-force）做可视化。
      </p>
      <ul class="legend">
        <li v-for="n in nodes.slice(0, 50)" :key="n.id" :class="n.type.toLowerCase()">
          <span class="dot"></span>{{ n.type }} · {{ n.label }}
        </li>
      </ul>
    </div>
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
.err {
  color: var(--danger);
}
.canvas {
  min-height: 200px;
}
.placeholder {
  color: var(--muted);
  font-size: 13px;
  margin: 0 0 12px;
}
.legend {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px;
}
li {
  font-size: 12px;
  color: var(--text);
}
.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent);
  margin-right: 6px;
}
li.topic .dot {
  background: #1d9e75;
}
li.event .dot {
  background: var(--accent);
}
</style>
