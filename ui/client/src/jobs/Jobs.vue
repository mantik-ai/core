<template>
    <div>
      <h2>Jobs</h2>
      <div v-if="loaded">
        <h3>Pending/Running Jobs</h3>
        <JobList v-bind:jobs="running_jobs"/>
        <h3>Finished Jobs</h3>
        <JobList v-bind:jobs="finished_jobs"/>
      </div>
      <div v-else>
        Loading...
      </div>
    </div>

</template>

<script>
import api from "@/api";
import JobList from "@/jobs/components/JobList";

export default {
  name: "Jobs",
  data() {
    return {
      running_jobs: null,
      finished_jobs: null,
      jobs: null,
      loaded: false,
      ended: false
    }
  },
  mounted(){
    this.ended = false;
    this.updateJobs();
  },
  beforeUnmount() {
    this.ended = true;
  },
  methods: {
    updateJobs(){
      api.jobs().then((response) => {
        this.onResponse(response);
      })
    },
    onResponse(response) {
      this.jobs = response.data.jobs;
      this.version = response.data.version;
      this.running_jobs = this.jobs.filter(e => e.state === 'pending' || e.state === 'running');
      this.finished_jobs = this.jobs.filter(e => e.state === 'done' || e.state === 'failed');
      this.loaded = true;
      this.nextPoll()
    },
    nextPoll(){
      if (!this.ended){
        this.promise = api.pollJobs(this.version).then((response) => {
          this.onResponse(response);
        })
      }
    }
  },
  components: {
    JobList
  }
}
</script>

<style scoped>

</style>