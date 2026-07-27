<script setup lang="ts">
import { NConfigProvider, NMessageProvider, type GlobalThemeOverrides } from 'naive-ui'
import { RouterView } from 'vue-router'

const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#167a68',
    primaryColorHover: '#1d8d79',
    primaryColorPressed: '#0f6254',
    primaryColorSuppl: '#167a68',
    infoColor: '#2563eb',
    successColor: '#167a68',
    warningColor: '#d97706',
    errorColor: '#dc4c4c',
    borderRadius: '10px',
    borderRadiusSmall: '8px',
    fontFamily:
      '"Inter", "PingFang SC", "Microsoft YaHei", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif'
  },
  Button: {
    heightMedium: '40px',
    borderRadiusMedium: '10px',
    fontWeight: '600'
  },
  Card: {
    borderRadius: '16px'
  },
  Input: {
    borderRadius: '10px'
  }
}
</script>

<template>
  <n-config-provider :theme-overrides="themeOverrides">
    <n-message-provider>
      <RouterView />
    </n-message-provider>
  </n-config-provider>
</template>
