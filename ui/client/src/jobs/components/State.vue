<template>
  <div>
    <Tag v-bind:severity="severity" v-bind:value="renderValue"></Tag>
  </div>
</template>

<script>
import Tag from 'primevue/tag';
// Renders begin/end/state of a Job or a Operation
export default {
  name: "State",
  props: ["value"],
  data() {
    return {
      elapsed: "",
      renderValue: "",
      severity: null,
      timer: null
    }
  },
  watch: {
    value(){
      this.updateElapsed();
    }
  },
  mounted() {
    this.timer = setInterval(() => this.updateElapsed(), 1000);
    this.updateElapsed();
  },
  beforeUnmount() {
    clearInterval(this.timer);
  },
  methods: {
    delta(start, end){
      const seconds = Math.floor((end.getTime() - start.getTime()) / 1000);
      const minutes = Math.floor(seconds / 60);
      const secondsAfterMinutes = seconds - (minutes * 60);
      if (minutes > 0){
        return `${minutes}m${secondsAfterMinutes}s`;
      } else {
        return `${seconds}s`;
      }
    },
    updateElapsed(){
      if (this.value.start != null && this.value.end != null){
        this.elapsed = this.delta(new Date(this.value.start), new Date(this.value.end));
      } else  if (this.value.start != null) {
        this.elapsed = this.delta(new Date(this.value.start), new Date());
      } else {
        this.elapsed = null;
      }
      this.updateRender();
    },
    updateRender(){
      function set(instance, wording, severity) {
        if (instance.elapsed != null){
          instance.renderValue = wording + " " + instance.elapsed;
        } else {
          instance.renderValue = wording;
        }
        instance.severity = severity;
      }

      switch(this.value.state){
        case "pending":
          set(this,"Pending", "info");
          break;
        case "running":
          set(this,"Running", "info");
          break;
        case "failed":
          set(this,"Failed", "danger");
          break;
        case "done":
          set(this,"Done", "success");
          break;
        default:
          set(this, this.value.state, "info");
      }
    }
  },
  components: {
    Tag
  }
}
</script>

<style scoped>

</style>