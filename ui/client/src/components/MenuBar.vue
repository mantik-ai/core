<template>
  <div>
    <PrimeMenubar :model="items">
      <template #start>
        <img :src="require(`@/assets/logo.png`)" height="32"/>
      </template>
      <!-- TODO: Add Mantik Logo here -->
      <template #end>
        <PrimeButton type="button" label="About" @click="toggle" />
        <PrimeMenu ref="rightmenu" :popup="true" :model="rightitems"/>
      </template>
    </PrimeMenubar>
  </div>
</template>

<script>
import PrimeMenubar from 'primevue/menubar';
import PrimeMenu from 'primevue/menu';
import PrimeButton from 'primevue/button';

export default {
  name: "MenuBar",
  data() {
    return {
      items: [
        {
          label: 'Home',
          to: '/',
          icon: 'pi pi-home'
        },
        {
          label: 'Jobs',
          to: '/jobs'
        }
      ],
      rightitems: [
        {
          label: "Version",
          to: '/version'
        }
      ]
    }
  },
  methods: {
    toggle: function(event){
      this.$refs.rightmenu.toggle(event);
    }
  },
  components: {
    PrimeMenubar,
    PrimeMenu,
    PrimeButton
  }
}
</script>

<style scoped>

</style>