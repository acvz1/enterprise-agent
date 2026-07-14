<template>
  <div class="dashboard-container">
    <div class="dashboard-header">
      <h2>📊 数据分析 Dashboard</h2>
      <n-button @click="refreshData" :loading="loading">
        <template #icon>
          <n-icon><refresh-outline /></n-icon>
        </template>
        刷新数据
      </n-button>
    </div>

    <n-spin :show="loading">
      <!-- 统计卡片区域 -->
      <n-grid :cols="4" :x-gap="16" :y-gap="16" class="stats-grid">
        <!-- 今日问答数量 -->
        <n-grid-item>
          <n-card class="stat-card" hoverable>
            <div class="stat-icon" style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);">
              <n-icon size="32" color="white"><chatbubbles-outline /></n-icon>
            </div>
            <div class="stat-content">
              <div class="stat-label">今日问答</div>
              <div class="stat-value">{{ stats.todayQaCount || 0 }}</div>
            </div>
          </n-card>
        </n-grid-item>

        <!-- 平均评分 -->
        <n-grid-item>
          <n-card class="stat-card" hoverable>
            <div class="stat-icon" style="background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);">
              <n-icon size="32" color="white"><star-outline /></n-icon>
            </div>
            <div class="stat-content">
              <div class="stat-label">平均评分</div>
              <div class="stat-value">{{ (stats.todayAvgScore * 100 || 0).toFixed(0) }}%</div>
            </div>
          </n-card>
        </n-grid-item>

        <!-- 缓存命中率 -->
        <n-grid-item>
          <n-card class="stat-card" hoverable>
            <div class="stat-icon" style="background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);">
              <n-icon size="32" color="white"><flash-outline /></n-icon>
            </div>
            <div class="stat-content">
              <div class="stat-label">缓存命中率</div>
              <div class="stat-value">{{ stats.cacheHitRate?.hitRate || 0 }}%</div>
            </div>
          </n-card>
        </n-grid-item>

        <!-- 优秀回答数 -->
        <n-grid-item>
          <n-card class="stat-card" hoverable>
            <div class="stat-icon" style="background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);">
              <n-icon size="32" color="white"><trophy-outline /></n-icon>
            </div>
            <div class="stat-content">
              <div class="stat-label">优秀回答</div>
              <div class="stat-value">{{ stats.scoreDistribution?.['优秀'] || 0 }}</div>
            </div>
          </n-card>
        </n-grid-item>
      </n-grid>

      <!-- 图表区域 -->
      <n-grid :cols="2" :x-gap="16" :y-gap="16" class="charts-grid">
        <!-- 评分分布饼图 -->
        <n-grid-item>
          <n-card title="📊 评分分布" hoverable>
            <div ref="scoreChartRef" style="height: 300px;"></div>
          </n-card>
        </n-grid-item>

        <!-- 模型响应时间对比 -->
        <n-grid-item>
          <n-card title="⚡ 模型响应时间对比" hoverable>
            <div ref="responseTimeChartRef" style="height: 300px;"></div>
          </n-card>
        </n-grid-item>

        <!-- 模型评分对比 -->
        <n-grid-item>
          <n-card title="⭐ 模型评分对比" hoverable>
            <div ref="modelScoreChartRef" style="height: 300px;"></div>
          </n-card>
        </n-grid-item>

        <!-- 评分指标雷达图 -->
        <n-grid-item>
          <n-card title="🎯 评分指标分析" hoverable>
            <div ref="metricsChartRef" style="height: 300px;"></div>
          </n-card>
        </n-grid-item>
      </n-grid>

      <!-- 最近问答记录 -->
      <n-card title="💬 最近问答记录" hoverable style="margin-top: 16px;">
        <n-config-provider>
          <n-data-table
            :columns="columns"
            :data="stats.recentQa || []"
            :pagination="pagination"
            :bordered="false"
            :single-line="false"
          />
        </n-config-provider>
      </n-card>
    </n-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue';
import { NCard, NGrid, NGridItem, NButton, NIcon, NSpin, NDataTable, NConfigProvider, useMessage } from 'naive-ui';
import { 
  ChatbubblesOutline, 
  StarOutline, 
  FlashOutline, 
  TrophyOutline,
  RefreshOutline 
} from '@vicons/ionicons5';
import { analyticsApi } from '@/services/api';
import * as echarts from 'echarts';

const message = useMessage();
const loading = ref(false);
const stats = ref<any>({});

// 图表实例
const scoreChartRef = ref<HTMLElement>();
const responseTimeChartRef = ref<HTMLElement>();
const modelScoreChartRef = ref<HTMLElement>();
const metricsChartRef = ref<HTMLElement>();

// 表格列定义
const columns = [
  {
    title: '问题',
    key: 'question',
    width: 250,
    ellipsis: {
      tooltip: true
    }
  },
  {
    title: '回答摘要',
    key: 'answer',
    width: 300,
    ellipsis: {
      tooltip: true
    }
  },
  {
    title: '评分',
    key: 'score',
    width: 100,
    render: (row: any) => {
      return `${(row.score * 100).toFixed(0)}%`;
    }
  },
  {
    title: '级别',
    key: 'level',
    width: 80
  },
  {
    title: '模型',
    key: 'model',
    width: 100
  },
  {
    title: '响应时间',
    key: 'responseTime',
    width: 100,
    render: (row: any) => {
      return `${row.responseTime}ms`;
    }
  },
  {
    title: '时间',
    key: 'createdAt',
    width: 180,
    render: (row: any) => {
      return new Date(row.createdAt).toLocaleString('zh-CN');
    }
  }
];

const pagination = {
  pageSize: 10
};

// 刷新数据
const refreshData = async () => {
  loading.value = true;
  try {
    const data: any = await analyticsApi.getDashboardStats();
    stats.value = data;
    
    // 等待DOM更新后渲染图表
    await nextTick();
    renderCharts();
    
    message.success('数据刷新成功');
  } catch (error) {
    console.error('获取Dashboard数据失败:', error);
    message.error('获取数据失败');
  } finally {
    loading.value = false;
  }
};

// 渲染所有图表
const renderCharts = () => {
  renderScoreDistributionChart();
  renderResponseTimeChart();
  renderModelScoreChart();
  renderMetricsChart();
};

// 渲染评分分布饼图
const renderScoreDistributionChart = () => {
  if (!scoreChartRef.value) return;
  
  const chart = echarts.init(scoreChartRef.value);
  const distribution = stats.value.scoreDistribution || {};
  
  const data = Object.keys(distribution).map(key => ({
    name: key,
    value: distribution[key]
  }));
  
  chart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      bottom: 0
    },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: false,
          position: 'center'
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 20,
            fontWeight: 'bold'
          }
        },
        labelLine: {
          show: false
        },
        data: data,
        color: ['#18a058', '#2080f0', '#f0a020', '#d03050']
      }
    ]
  });
};

// 渲染模型响应时间柱状图
const renderResponseTimeChart = () => {
  if (!responseTimeChartRef.value) return;
  
  const chart = echarts.init(responseTimeChartRef.value);
  const responseTimes = stats.value.modelResponseTimes || {};
  
  const models = Object.keys(responseTimes);
  const times = Object.values(responseTimes);
  
  chart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: '{b}: {c}ms'
    },
    xAxis: {
      type: 'category',
      data: models
    },
    yAxis: {
      type: 'value',
      name: '响应时间(ms)'
    },
    series: [
      {
        type: 'bar',
        data: times,
        itemStyle: {
          borderRadius: [4, 4, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#4facfe' },
            { offset: 1, color: '#00f2fe' }
          ])
        },
        label: {
          show: true,
          position: 'top',
          formatter: '{c}ms'
        }
      }
    ]
  });
};

// 渲染模型评分对比柱状图
const renderModelScoreChart = () => {
  if (!modelScoreChartRef.value) return;
  
  const chart = echarts.init(modelScoreChartRef.value);
  const modelScores = stats.value.modelAvgScores || {};
  
  const models = Object.keys(modelScores);
  const scores = Object.values(modelScores).map((s: any) => (s * 100).toFixed(0));
  
  chart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: '{b}: {c}%'
    },
    xAxis: {
      type: 'category',
      data: models
    },
    yAxis: {
      type: 'value',
      name: '评分(%)',
      max: 100
    },
    series: [
      {
        type: 'bar',
        data: scores,
        itemStyle: {
          borderRadius: [4, 4, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#f093fb' },
            { offset: 1, color: '#f5576c' }
          ])
        },
        label: {
          show: true,
          position: 'top',
          formatter: '{c}%'
        }
      }
    ]
  });
};

// 渲染评分指标雷达图
const renderMetricsChart = () => {
  if (!metricsChartRef.value) return;
  
  const chart = echarts.init(metricsChartRef.value);
  const metrics = stats.value.avgMetrics || {};
  
  chart.setOption({
    tooltip: {},
    radar: {
      indicator: [
        { name: '相关性', max: 1 },
        { name: '完整性', max: 1 },
        { name: '无幻觉', max: 1 }
      ]
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            value: [
              metrics.relevance || 0,
              metrics.completeness || 0,
              1 - (metrics.hallucination || 0)  // 反转幻觉分数
            ],
            name: '平均指标',
            areaStyle: {
              color: 'rgba(67, 233, 123, 0.3)'
            },
            lineStyle: {
              color: '#43e97b'
            },
            itemStyle: {
              color: '#43e97b'
            }
          }
        ]
      }
    ]
  });
};

onMounted(() => {
  refreshData();
});
</script>

<style scoped>
.dashboard-container {
  padding: 24px;
  background-color: #f5f7fa;
  min-height: 100%;  /* 改为 100% 而不是 100vh */
  width: 100%;
  box-sizing: border-box;
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.dashboard-header h2 {
  margin: 0;
  font-size: 24px;
  color: #333;
}

.stats-grid {
  margin-bottom: 16px;
}

.stat-card {
  position: relative;
  overflow: hidden;
}

.stat-card :deep(.n-card__content) {
  padding: 20px;
  display: flex;
  align-items: center;
}

.stat-icon {
  width: 64px;
  height: 64px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.stat-content {
  flex: 1;
}

.stat-label {
  font-size: 14px;
  color: #666;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #333;
}

.charts-grid {
  margin-top: 16px;
}
</style>
