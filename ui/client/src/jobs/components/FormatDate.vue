<template>
<span>
  {{formatted}}
</span>
</template>

<script>
/* Formats a date in User format */
export default {
  name: "FormatDate",
  props: ["date"],
  data() {
    return {
      formatted: this.date != null ? new Date(this.date).toLocaleString(): ""
    }
  }
}
</script>

<style scoped>

</style>