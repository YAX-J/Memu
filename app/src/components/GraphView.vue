<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount } from "vue";
import {
  forceSimulation,
  forceLink,
  forceManyBody,
  forceCenter,
  forceCollide,
  type Simulation,
} from "d3-force";
import { fetchSubgraph } from "../api/client";

interface GNode {
  id: string;
  type: string;
  label: string;
  x?: number;
  y?: number;
}
interface GLink {
  source: GNode;
  target: GNode;
  type: string;
}

const nodes = reactive<GNode[]>([]);
const links = reactive<GLink[]>([]);
const error = ref("");
const W = 760;
const H = 500;

let sim: Simulation<GNode, GLink> | null = null;

const COLORS: Record<string, string> = {
  person: "#4f6ef7",
  topic: "#e0a800",
  project: "#b455f5",
  task: "#1d9e75",
};
const DEFAULT_COLOR = "#8a94a6";

function color(type: string): string {
  return COLORS[type.toLowerCase()] ?? DEFAULT_COLOR;
}

function isEntity(type: string): boolean {
  const t = type.toLowerCase();
  return t === "person" || t === "topic" || t === "project" || t === "task";
}

function radius(type: string): number {
  return isEntity(type) ? 11 : 6;
}

async function load() {
  error.value = "";
  try {
    const g = await fetchSubgraph();
    nodes.length = 0;
    links.length = 0;

    const byId = new Map<string, GNode>();
    for (const n of g.nodes) {
      const node: GNode = {
        id: n.id,
        type: n.type,
        label: n.label,
        x: W / 2 + (Math.random() - 0.5) * 160,
        y: H / 2 + (Math.random() - 0.5) * 160,
      };
      byId.set(n.id, node);
      nodes.push(node);
    }
    for (const e of g.edges) {
      const s = byId.get(e.source);
      const t = byId.get(e.target);
      if (s && t) links.push({ source: s, target: t, type: e.type });
    }
    run();
  } catch (e) {
    error.value = (e as Error).message;
  }
}

function run() {
  sim?.stop();
  sim = forceSimulation<GNode>(nodes)
    .force("link", forceLink<GNode, GLink>(links).id((d) => d.id).distance(72))
    .force("charge", forceManyBody().strength(-230))
    .force("center", forceCenter(W / 2, H / 2))
    .force("collide", forceCollide(22));
}

onMounted(load);
onBeforeUnmount(() => sim?.stop());
</script>

<template>
  <section class="panel">
    <header>
      <h2>知识图谱</h2>
      <button @click="load">刷新</button>
    </header>
    <p v-if="error" class="err">加载失败：{{ error }}</p>
    <div v-else class="canvas">
      <svg :viewBox="`0 0 ${W} ${H}`" preserveAspectRatio="xMidYMid meet">
        <g class="links">
          <line
            v-for="l in links"
            :key="`${l.source.id}-${l.target.id}`"
            :class="l.type.toLowerCase()"
            :x1="l.source.x"
            :y1="l.source.y"
            :x2="l.target.x"
            :y2="l.target.y"
          />
        </g>
        <g class="nodes">
          <g v-for="n in nodes" :key="n.id">
            <circle
              :cx="n.x"
              :cy="n.y"
              :r="radius(n.type)"
              :fill="color(n.type)"
            />
            <text
              :x="n.x"
              :y="(n.y ?? 0) - radius(n.type) - 5"
              text-anchor="middle"
              class="label"
            >
              {{ n.label }}
            </text>
          </g>
        </g>
      </svg>
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
  border: 1px solid var(--border);
  border-radius: 8px;
  overflow: hidden;
}
svg {
  width: 100%;
  height: auto;
  display: block;
}
.links line {
  stroke: var(--border);
  stroke-width: 1.2;
}
.links line.participates_in {
  stroke: #4f6ef7;
  stroke-opacity: 0.35;
}
.links line.relates_to {
  stroke: var(--border);
}
.label {
  font-size: 11px;
  fill: var(--text);
  pointer-events: none;
}
</style>
