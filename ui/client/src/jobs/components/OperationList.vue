<template>
<DataTable :value="opsPrepared" v-if="opsPrepared.length > 0">
  <Column field="num" header="#"/>
  <Column field="value" header="State">
    <template #body="slotProps">
      <State :value="slotProps.data.value"/>
    </template>
  </Column>
  <Column field="type" header="Type">
    <template #body="slotProps">
      <span v-if="slotProps.data.url != null">
        <a :href="slotProps.data.url" v-text="slotProps.data.type"/>
      </span>
      <span v-else>
        {{slotProps.data.type}}
      </span>
    </template>
  </Column>
  <Column field="error" header="Error"/>
</DataTable>
  <div v-else>
    No Operations
  </div>
</template>

<script>
import DataTable from "primevue/datatable";
import Column from "primevue/column";
import State from "@/jobs/components/State";
export default {
  name: "OperationList",
  props: ["operations", "jobId"],
  components: {
    DataTable, Column, State

  },
  data: function (){
    return {
      opsPrepared: this.transformOpList(this.operations)
    }
  },
  watch: {
    operations: function(newValue){
      this.opsPrepared = this.transformOpList(newValue);
    }
  },
  methods: {
    transformOpList(operations) {
      let self = this
      return operations.map(function(v, idx){
        var url = null;
        switch (v.definition.type) {
          case 'run_graph':
            url = `/jobs/${self.jobId}/operations/${v.id}/graph`
            break;
        }
        console.log("Link=" + url);
        return {
          id: v.id,
          num: idx,
          value: v,
          state: v.state,
          type: v.definition.type,
          error: v.error == null ? "" : v.error,
          url: url
        }
      })
    }
  }
}
</script>

<style scoped>

</style>