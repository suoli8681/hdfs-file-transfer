<template>
  <div style="display:flex;height:calc(100vh - 50px)">
    <!-- 会话列表 -->
    <div style="width:260px;background:#fff;border-right:1px solid #e6e6e6;display:flex;flex-direction:column">
      <div style="padding:12px;border-bottom:1px solid #f0f0f0">
        <el-button type="primary" size="small" style="width:100%" @click="handleNewConversation">新建对话</el-button>
      </div>
      <div style="flex:1;overflow:auto">
        <div v-for="conv in conversations" :key="conv.id"
          @click="selectConversation(conv.id)"
          :style="{
            padding: '10px 14px',
            cursor: 'pointer',
            borderBottom: '1px solid #f5f5f5',
            background: conv.id === currentConvId ? '#ecf5ff' : '',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center'
          }">
          <div style="flex:1;overflow:hidden">
            <div style="font-size:13px;font-weight:500;color:#303133;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">{{ conv.title }}</div>
            <div style="font-size:11px;color:#999;margin-top:2px">{{ formatTime(conv.updateTime) }}</div>
          </div>
          <el-popconfirm v-if="conv.id === currentConvId" title="确定删除该会话?" @confirm="handleDelete(conv.id)">
            <template #reference><el-icon style="color:#f56c6c;flex-shrink:0;cursor:pointer"><Delete /></el-icon></template>
          </el-popconfirm>
        </div>
        <div v-if="conversations.length === 0" style="text-align:center;color:#999;padding:30px;font-size:13px">暂无会话</div>
      </div>
    </div>

    <!-- 消息区域 -->
    <div style="flex:1;display:flex;flex-direction:column;background:#f5f5f5">
      <div v-if="!currentConvId" style="flex:1;display:flex;align-items:center;justify-content:center;color:#999">
        <div style="text-align:center">
          <el-icon style="font-size:48px;margin-bottom:10px"><ChatLineRound /></el-icon>
          <div>选择一个会话或新建对话开始</div>
        </div>
      </div>
      <template v-else>
        <div ref="msgContainer" style="flex:1;overflow:auto;padding:20px">
          <div v-for="msg in messages" :key="msg.id" style="margin-bottom:20px">
            <!-- 用户消息 -->
            <div v-if="msg.role === 'user'" style="display:flex;justify-content:flex-end">
              <div style="max-width:70%;background:#409EFF;color:#fff;padding:10px 16px;border-radius:12px 12px 4px 12px;font-size:14px;line-height:1.6;white-space:pre-wrap;word-break:break-word">{{ msg.content }}</div>
            </div>
            <!-- AI 消息 -->
            <div v-else style="display:flex;justify-content:flex-start">
              <div style="max-width:70%;background:#fff;padding:10px 16px;border-radius:12px 12px 12px 4px;font-size:14px;line-height:1.6;white-space:pre-wrap;word-break:break-word;box-shadow:0 1px 2px rgba(0,0,0,0.08)">{{ msg.content }}</div>
            </div>
          </div>
          <!-- 正在输出 -->
          <div v-if="streaming" style="display:flex;justify-content:flex-start">
            <div style="max-width:70%;background:#fff;padding:10px 16px;border-radius:12px 12px 12px 4px;font-size:14px;line-height:1.6;white-space:pre-wrap;word-break:break-word;box-shadow:0 1px 2px rgba(0,0,0,0.08)">
              {{ streamingContent }}
              <span style="display:inline-block;width:6px;height:14px;background:#409EFF;animation:blink 1s infinite;vertical-align:middle"></span>
            </div>
          </div>
        </div>
        <!-- 输入框 -->
        <div style="padding:10px 20px;background:#fff;border-top:1px solid #e6e6e6">
          <div style="margin-bottom:8px;display:flex;align-items:center;gap:8px">
            <span style="font-size:12px;color:#909399">模型</span>
            <el-select v-model="selectedConfigId" placeholder="选择模型" size="small" style="width:200px">
              <el-option v-for="c in configs" :key="c.id" :label="c.modelName" :value="c.id">
                <span>{{ c.modelName }}</span>
                <span style="float:right;color:#8492a6;font-size:12px">{{ c.configName }}</span>
              </el-option>
            </el-select>
          </div>
          <div style="display:flex;gap:10px;align-items:flex-end">
            <el-input v-model="inputText" type="textarea" :rows="2" placeholder="输入问题，如：查看源端 /public-data 目录下的迁移情况" style="flex:1" @keydown.enter.exact.prevent="handleSend" />
            <el-button type="primary" :loading="streaming" @click="handleSend" :disabled="!inputText.trim()">发送</el-button>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { createConversation, listConversations, getMessages, deleteConversation, listConfigs } from '../api/ai'
import { ElMessage } from 'element-plus'

const conversations = ref([])
const currentConvId = ref(null)
const messages = ref([])
const inputText = ref('')
const streaming = ref(false)
const streamingContent = ref('')
const msgContainer = ref(null)
const configs = ref([])
const selectedConfigId = ref(null)

const formatTime = (t) => {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 16)
}

const fetchConversations = async () => {
  try {
    conversations.value = await listConversations() || []
  } catch (e) { conversations.value = [] }
}

const selectConversation = async (id) => {
  currentConvId.value = id
  messages.value = []
  streamingContent.value = ''
  try {
    messages.value = await getMessages(id) || []
    nextTick(() => { if (msgContainer.value) msgContainer.value.scrollTop = msgContainer.value.scrollHeight })
  } catch (e) { messages.value = [] }
}

const handleNewConversation = async () => {
  try {
    const id = await createConversation()
    await fetchConversations()
    await selectConversation(id)
  } catch (e) { ElMessage.error('创建会话失败') }
}

const handleDelete = async (id) => {
  try {
    await deleteConversation(id)
    ElMessage.success('已删除')
    currentConvId.value = null
    messages.value = []
    fetchConversations()
  } catch (e) { ElMessage.error(e.message || '删除失败') }
}

const handleSend = async () => {
  if (!inputText.value.trim() || streaming.value) return
  const text = inputText.value.trim()
  inputText.value = ''

  messages.value.push({ id: 'temp-' + Date.now(), role: 'user', content: text })
  nextTick(() => { if (msgContainer.value) msgContainer.value.scrollTop = msgContainer.value.scrollHeight })

  streaming.value = true
  streamingContent.value = ''

  const token = localStorage.getItem('token')
  const configParam = selectedConfigId.value ? `&configId=${selectedConfigId.value}` : ''
  const url = `/api/ai/chat?conversationId=${currentConvId.value}&message=${encodeURIComponent(text)}&token=${token}${configParam}`

  const eventSource = new EventSource(url)
  eventSource.addEventListener('content', (e) => {
    streamingContent.value += e.data
    nextTick(() => { if (msgContainer.value) msgContainer.value.scrollTop = msgContainer.value.scrollHeight })
  })
  eventSource.addEventListener('tool', (e) => {
    streamingContent.value += '\n\n[正在查询迁移数据...]\n'
  })
  eventSource.addEventListener('error', (e) => {
    if (e.data) {
      streamingContent.value += '\n\n[错误] ' + e.data
    }
    eventSource.close()
    streaming.value = false
    messages.value.push({ id: 'ai-' + Date.now(), role: 'assistant', content: streamingContent.value })
    streamingContent.value = ''
    fetchConversations()
  })
  eventSource.addEventListener('done', () => {
    eventSource.close()
    streaming.value = false
    if (streamingContent.value) {
      messages.value.push({ id: 'ai-' + Date.now(), role: 'assistant', content: streamingContent.value })
      streamingContent.value = ''
    }
    fetchConversations()
    getMessages(currentConvId.value).then(res => {
      messages.value = res || []
    })
  })
}

const fetchConfigs = async () => {
  try {
    configs.value = await listConfigs() || []
    const def = configs.value.find(c => c.isDefault === 1)
    if (def) selectedConfigId.value = def.id
    else if (configs.value.length > 0) selectedConfigId.value = configs.value[0].id
  } catch (e) { configs.value = [] }
}

onMounted(() => {
  fetchConversations()
  fetchConfigs()
})
</script>

<style scoped>
@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}
</style>
