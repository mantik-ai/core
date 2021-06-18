<template>
<div>
  <h2>Run Graph</h2>
  <div v-if="loaded">
    <dl>
      <dt>Nodes</dt>
      <dd>{{nodeCount}}</dd>
    </dl>
    <div v-html="rendered" style="width: 100%">

    </div>
  </div>
  <div v-else>
    Loading...
  </div>
</div>
</template>

<script>
import api from "@/api";
import mermaid from "mermaid";
import runGraphRender from "@/jobs/runGraphRender";

mermaid.mermaidAPI.initialize({
  startOnLoad:false
});

export default {
  name: "RunGraph",
  data() {
    return {
      jobId: this.$route.params.jobId,
      operationId: this.$route.params.operationId,
      graph: null,
      nodeCount: null,
      version: null,
      loaded: false,
      ended: false,
      rendered: null
    }
  },
  mounted() {
    this.updateGraph()
  },
  beforeUnmount() {
    this.ended = true
  },
  methods: {
    updateGraph(){
      api.graph(this.jobId, this.operationId).then((response) => {
        this.onResponse(response);
      })
    },
    pollGraph() {
      if (!this.ended) {
        api.pollGraph(this.jobId, this.operationId, this.version).then((response) => {
          this.onResponse(response);
        })
      }
    },
    onResponse(response){
      this.version = response.data.version;
      this.graph = response.data.graph;
      this.nodeCount = Object.keys(this.graph.nodes).length;
      this.loaded = true;
      this.renderGraph();
      if (!this.ended) {
        this.pollGraph()
      }
    },
    renderGraph(){
      const mermaidCode = runGraphRender(this.graph);
      console.log(`Rendered Code\n${mermaidCode}`)
      this.rendered = mermaid.render("rendered-graph", mermaidCode, (rendered) => {
        this.rendered = rendered;
      });
    }
  }
}
</script>

<style scoped>

</style>