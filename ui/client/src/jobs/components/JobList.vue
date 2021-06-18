<template>
  <div>
    <div v-if="jobsPrepared.length == 0">
      No Jobs
    </div>
    <div v-else>
      <DataTable :value="jobsPrepared">
        <Column field="name" header="Name">
          <template #body="slotProps">
            <a :href="slotProps.data.url" v-text="slotProps.data.name" />
          </template>
        </Column>
        <Column field="value" header="State">
          <template #body="slotProps">
            <State :value="slotProps.data.value"/>
          </template>
        </Column>
        <Column field="start" header="Start"/>
        <Column field="error" header="Error"/>
      </DataTable>
    </div>
  </div>
</template>

<script>
import DataTable from 'primevue/datatable';
import Column from 'primevue/column';
import State from "@/jobs/components/State";

export default {
  name: "JobList",
  props: ["jobs"],
  data: function (){
    return {
      jobsPrepared: this.transformJobList(this.jobs)
    }
  },
  components: {
    DataTable,
    Column,
    State
  },
  watch: {
    jobs(newValue) {
      this.setJobList(newValue)
    },
  },
  methods: {
    setJobList(jobs) {
      this.jobsPrepared = this.transformJobList(jobs);
    },
    transformJobList(jobs) {
      return jobs.map(function(v){
        return {
          id: v.id,
          value: v,
          state: v.state,
          url: "/jobs/" + v.id,
          name: v.name != null ? v.name : v.id,
          start: v.start != null ? new Date(v.start).toLocaleString(): "",
          error: v.error == null ? "" : v.error
        }
      })
    }
  }
}
</script>

<style scoped>

</style>