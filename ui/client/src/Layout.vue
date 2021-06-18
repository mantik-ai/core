<template>
  <div id="layout">
    <MenuBar></MenuBar>

    <div class="p-grid">
      <div class="p-col-2"></div>
      <div class="p-col-8">
        <router-view></router-view>
      </div>
      <div class="p-col-2"></div>
    </div>

  </div>
</template>

<script>
import MenuBar from "@/components/MenuBar";

export default {
  name: "Layout.vue",
  components: {
    MenuBar
  }
}
</script>

<style scoped>
#layout {

}
</style>