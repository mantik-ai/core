<template>
<div>
<h2>Job</h2>
  <div v-if="loaded">
    <dl>
      <dt>ID</dt>
      <dd>{{job.header.id}}</dd>
      <dt>Name</dt>
      <dd v-if="job.header.name">
        {{job.header.name}}
      </dd>
      <dd v-else>
        Not named
      </dd>
      <dt>State</dt>
      <dd><State :value="job.header"/></dd>
      <dt>Registration Time</dt>
      <dd>
        <FormatDate v-bind:date="job.header.registration"/>
      </dd>
      <dt>Start Time</dt>
      <dd><FormatDate :date="job.header.start"/></dd>
      <dt>End Time</dt>
      <dd><FormatDate :date="job.header.end"/></dd>
    </dl>

    <h3>Operations</h3>
    <OperationList v-bind:operations="job.operations" v-bind:job-id="jobId"/>
  </div>
  <div v-else>
    Loading...
  </div>
</div>
</template>

<script>
import api from '@/api';
import State from "@/jobs/components/State";
import OperationList from "@/jobs/components/OperationList";
import FormatDate from "@/jobs/components/FormatDate";

export default {
  name: "Job",
  components: {OperationList, State, FormatDate},
  data() {
    return {
      "jobId": this.$route.params.jobId,
      "job": null,
      "version": null,
      "loaded": false,
      "ended": false
    }
  },
  mounted() {
    this.ended = false;
    this.updateJob()
  },
  beforeUnmount() {
    this.ended = true;
  },
  methods: {
    updateJob(){
      api.job(this.jobId).then((response) => {
        this.onResponse(response);
      })
    },
    onResponse(response) {
      this.job = response.data.job;
      this.version = response.data.version;
      this.loaded = true;
      this.nextPoll();
    },
    nextPoll() {
      if (!this.ended){
        api.pollJob(this.jobId, this.version).then((response) => {
          this.onResponse(response);
        })
      }
    }
  }
}
</script>

<style scoped>

</style>