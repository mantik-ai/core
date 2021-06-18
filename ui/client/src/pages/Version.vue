<template>
  <div v-if="loaded">
    <h2>Mantik Engine</h2>
    <Message severity="success">Mantik Engine connected</Message>
    <dl>
      <dt>Version</dt>
      <dd>{{version.version}}</dd>
      <dt>Scala Version</dt>
      <dd>{{version.scalaVersion}}</dd>
      <dt>Executor Backend</dt>
      <dd>{{version.executorBackend}}</dd>
    </dl>
  </div>
  <div v-else>
    Loading...
  </div>
</template>

<script>
import api from '@/api';
import Message from 'primevue/message';

export default {
  name: "Version",
  data(){
    return {
      version: null,
      loaded: false
    }
  },
  mounted() {
    this.updateVersion();
  },
  methods: {
    updateVersion(){
      api.version().then ((response) => {
        this.version = response.data;
        this.loaded = true;
      });
    }
  },
  components: {
    Message
  }
}
</script>

<style scoped>

</style>