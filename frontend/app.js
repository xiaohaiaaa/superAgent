// API配置
const API_BASE_URL = 'http://localhost:9900/api';

// 当前活动面板
let currentPanel = 'chat';

// 会话管理（内部使用，不显示在UI）
const sessions = {
    chat: {
        id: '',
        messageCount: 0
    },
    stream: {
        id: '',
        messageCount: 0
    }
};

// DOM元素
const elements = {
    // 导航
    navLinks: document.querySelectorAll('.nav-link'),

    // 文件上传
    uploadBtn: document.getElementById('upload-btn'),
    uploadModal: document.getElementById('uploadModal'),
    uploadArea: document.getElementById('upload-area'),
    fileInput: document.getElementById('file-input'),
    fileInfo: document.getElementById('file-info'),
    fileName: document.getElementById('file-name'),
    removeFileBtn: document.getElementById('remove-file-btn'),
    uploadProgress: document.getElementById('upload-progress'),
    progressBar: document.getElementById('progress-bar'),
    progressText: document.getElementById('progress-text'),
    confirmUploadBtn: document.getElementById('confirm-upload-btn'),

    // 快速对话
    chatPanel: document.getElementById('chat-panel'),
    chatMessages: document.getElementById('chat-messages'),
    chatInput: document.getElementById('chat-input'),
    sendChatBtn: document.getElementById('send-chat-btn'),
    clearChatBtn: document.getElementById('clear-chat-btn'),

    // 流式对话
    streamPanel: document.getElementById('chat-stream-panel'),
    streamMessages: document.getElementById('stream-messages'),
    streamInput: document.getElementById('stream-input'),
    sendStreamBtn: document.getElementById('send-stream-btn'),
    clearStreamBtn: document.getElementById('clear-stream-btn'),

    // AI运维
    aiOpsPanel: document.getElementById('ai-ops-panel'),
    aiOpsOutput: document.getElementById('ai-ops-output'),
    startAiOpsBtn: document.getElementById('start-ai-ops-btn')
};

// 初始化
document.addEventListener('DOMContentLoaded', () => {
    initNavigation();
    initUploadPanel();
    initChatPanel();
    initStreamPanel();
    initAiOpsPanel();
});

// 导航初始化
function initNavigation() {
    elements.navLinks.forEach(link => {
        link.addEventListener('click', (e) => {
            e.preventDefault();
            const targetTab = link.dataset.tab;
            switchPanel(targetTab);
        });
    });
}

// 切换面板
function switchPanel(tabName) {
    // 更新导航状态
    elements.navLinks.forEach(link => {
        link.classList.remove('active');
        if (link.dataset.tab === tabName) {
            link.classList.add('active');
        }
    });

    // 切换面板显示
    document.querySelectorAll('.content-panel').forEach(panel => {
        panel.classList.remove('active');
    });

    switch(tabName) {
        case 'chat':
            elements.chatPanel.classList.add('active');
            break;
        case 'chat-stream':
            elements.streamPanel.classList.add('active');
            break;
        case 'ai-ops':
            elements.aiOpsPanel.classList.add('active');
            break;
    }

    currentPanel = tabName;
}

// ==================== 快速对话功能 ====================

function initChatPanel() {
    // 发送按钮点击
    elements.sendChatBtn.addEventListener('click', sendChatMessage);

    // 回车发送
    elements.chatInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') {
            sendChatMessage();
        }
    });

    // 清空对话
    elements.clearChatBtn.addEventListener('click', clearChatHistory);
}

// 发送聊天消息
async function sendChatMessage() {
    const question = elements.chatInput.value.trim();
    if (!question) {
        showToast('请输入问题', 'warning');
        return;
    }

    // 生成或使用现有会话ID
    if (!sessions.chat.id) {
        sessions.chat.id = generateSessionId();
    }

    // 添加用户消息到界面
    addChatMessage('user', question);
    elements.chatInput.value = '';
    elements.chatInput.disabled = true;
    elements.sendChatBtn.disabled = true;

    try {
        // 调用后端API
        const response = await fetch(`${API_BASE_URL}/chat`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                Id: sessions.chat.id,
                Question: question
            })
        });

        const result = await response.json();

        if (result.code === 200 && result.data.success) {
            // 显示AI回复
            addChatMessage('assistant', result.data.answer);
            sessions.chat.messageCount++;
        } else {
            // 显示错误信息
            addChatMessage('assistant', `错误: ${result.data?.errorMessage || result.message || '请求失败'}`, true);
        }
    } catch (error) {
        console.error('发送消息失败:', error);
        addChatMessage('assistant', '网络错误，请检查后端服务是否启动', true);
    } finally {
        elements.chatInput.disabled = false;
        elements.sendChatBtn.disabled = false;
        elements.chatInput.focus();
    }
}

// 添加聊天消息到界面
function addChatMessage(role, content, isError = false) {
    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${role}`;

    const avatar = document.createElement('div');
    avatar.className = 'message-avatar';
    avatar.innerHTML = role === 'user' ? '<i class="bi bi-person-fill"></i>' : '<i class="bi bi-robot"></i>';

    const messageContent = document.createElement('div');
    messageContent.className = 'message-content';
    if (isError) {
        messageContent.style.backgroundColor = '#fef2f2';
        messageContent.style.color = '#991b1b';
        messageContent.style.border = '2px solid #fecaca';
    }
    messageContent.innerHTML = `
        <div>${escapeHtml(content)}</div>
        <div class="message-time">${formatTime(new Date())}</div>
    `;

    messageDiv.appendChild(avatar);
    messageDiv.appendChild(messageContent);

    // 清除初始提示
    const emptyState = elements.chatMessages.querySelector('.empty-state');
    if (emptyState) {
        emptyState.remove();
    }

    elements.chatMessages.appendChild(messageDiv);
    elements.chatMessages.scrollTop = elements.chatMessages.scrollHeight;
}

// 清空聊天历史
async function clearChatHistory() {
    if (!sessions.chat.id) {
        showToast('没有可清空的对话记录', 'warning');
        return;
    }

    if (!confirm('确定要清空对话历史吗？')) {
        return;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/chat/clear`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                Id: sessions.chat.id
            })
        });

        const result = await response.json();

        if (result.code === 200) {
            elements.chatMessages.innerHTML = `
                <div class="empty-state">
                    <i class="bi bi-chat-dots"></i>
                    <p class="mb-0">开始你的对话吧！</p>
                </div>
            `;
            sessions.chat.id = '';
            sessions.chat.messageCount = 0;
            showToast('对话历史已清空', 'success');
        } else {
            showToast(result.message || '清空失败', 'error');
        }
    } catch (error) {
        console.error('清空会话失败:', error);
        showToast('网络错误', 'error');
    }
}

// ==================== 流式对话功能 ====================

function initStreamPanel() {
    elements.sendStreamBtn.addEventListener('click', sendStreamMessage);
    elements.streamInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') {
            sendStreamMessage();
        }
    });
    elements.clearStreamBtn.addEventListener('click', clearStreamHistory);
}

// 发送流式消息
async function sendStreamMessage() {
    const question = elements.streamInput.value.trim();
    if (!question) {
        showToast('请输入问题', 'warning');
        return;
    }

    // 生成或使用现有会话ID
    if (!sessions.stream.id) {
        sessions.stream.id = generateSessionId();
    }

    // 添加用户消息
    addStreamMessage('user', question);
    elements.streamInput.value = '';
    elements.streamInput.disabled = true;
    elements.sendStreamBtn.disabled = true;

    // 创建AI消息容器，显示加载动画
    const aiMessageId = 'ai-' + Date.now();
    const loadingContent = '<div class="loading-dots"><span></span><span></span><span></span></div>';
    addStreamMessage('assistant', loadingContent, false, aiMessageId);

    // 执行流式请求
    performStreamingRequest(sessions.stream.id, question, aiMessageId);
}

// 执行流式请求（使用fetch模拟SSE）
async function performStreamingRequest(sessionId, question, messageId) {
    let isCompleted = false;

    try {
        const response = await fetch(`${API_BASE_URL}/chat_stream`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'text/event-stream',
                'Cache-Control': 'no-cache'
            },
            body: JSON.stringify({
                Id: sessionId,
                Question: question
            })
        });

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder('utf-8');
        let buffer = '';
        let fullAnswer = '';

        // 处理单个事件的函数
        function processEvent(dataString) {
            if (!dataString.trim()) return;

            try {
                const data = JSON.parse(dataString.trim());
                console.log('收到事件:', data);

                if (data.type === 'content') {
                    fullAnswer += data.data || '';
                    updateStreamMessage(messageId, escapeHtml(fullAnswer) + '<span class="typing-indicator"></span>');
                } else if (data.type === 'error') {
                    updateStreamMessage(messageId, data.data || '未知错误', true);
                    isCompleted = true;
                } else if (data.type === 'done') {
                    updateStreamMessage(messageId, escapeHtml(fullAnswer));
                    sessions.stream.messageCount++;
                    isCompleted = true;
                }
            } catch (e) {
                console.error('解析SSE数据失败:', e, '原始数据:', dataString);
            }
        }

        while (true) {
            const { done, value } = await reader.read();
            if (done) {
                console.log('流读取完成');
                break;
            }

            buffer += decoder.decode(value, { stream: true });
            console.log('收到数据块:', buffer.length, '字符');

            // 按 \n\n 或 \r\n\r\n 分割事件
            const events = buffer.split(/\n\n|\r\n\r\n/);
            buffer = events.pop() || '';

            for (const event of events) {
                const dataLines = event.split('\n');
                for (const line of dataLines) {
                    if (line.trim().startsWith('data:')) {
                        const dataContent = line.slice(5).trim();
                        if (dataContent) {
                            processEvent(dataContent);
                            if (isCompleted) return;
                        }
                    }
                }
            }
        }

        // 处理缓冲区中剩余的数据
        if (buffer.trim()) {
            console.log('处理剩余缓冲数据');
            const dataLines = buffer.split('\n');
            for (const line of dataLines) {
                if (line.trim().startsWith('data:')) {
                    const dataContent = line.slice(5).trim();
                    if (dataContent) {
                        processEvent(dataContent);
                    }
                }
            }
        }

    } catch (error) {
        console.error('流式请求失败:', error);
        updateStreamMessage(messageId, '请求失败：' + error.message, true);
    } finally {
        console.log('清理资源，重新启用输入');
        elements.streamInput.disabled = false;
        elements.sendStreamBtn.disabled = false;
    }
}

// 添加流式消息
function addStreamMessage(role, content, isError = false, messageId = '') {
    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${role}`;
    if (messageId) {
        messageDiv.id = messageId;
    }

    const avatar = document.createElement('div');
    avatar.className = 'message-avatar';
    avatar.innerHTML = role === 'user' ? '<i class="bi bi-person-fill"></i>' : '<i class="bi bi-robot"></i>';

    const messageContent = document.createElement('div');
    messageContent.className = 'message-content';
    if (isError) {
        messageContent.style.backgroundColor = '#fef2f2';
        messageContent.style.color = '#991b1b';
        messageContent.style.border = '2px solid #fecaca';
    }
    messageContent.innerHTML = `
        <div>${content}</div>
        <div class="message-time">${formatTime(new Date())}</div>
    `;

    messageDiv.appendChild(avatar);
    messageDiv.appendChild(messageContent);

    // 清除初始提示
    const emptyState = elements.streamMessages.querySelector('.empty-state');
    if (emptyState) {
        emptyState.remove();
    }

    elements.streamMessages.appendChild(messageDiv);
    elements.streamMessages.scrollTop = elements.streamMessages.scrollHeight;
}

// 更新流式消息
function updateStreamMessage(messageId, content, isError = false) {
    const messageDiv = document.getElementById(messageId);
    if (!messageDiv) return;

    const messageContent = messageDiv.querySelector('.message-content > div');
    if (isError) {
        messageContent.parentElement.style.backgroundColor = '#fef2f2';
        messageContent.parentElement.style.color = '#991b1b';
        messageContent.parentElement.style.border = '2px solid #fecaca';
    }
    // 检查是否是首次内容更新（如果有加载动画）
    const hasLoadingAnimation = messageContent.querySelector('.loading-dots');
    if (hasLoadingAnimation && content) {
        messageContent.innerHTML = content;
    } else {
        messageContent.innerHTML = content;
    }
    elements.streamMessages.scrollTop = elements.streamMessages.scrollHeight;
}

// 清空流式历史
async function clearStreamHistory() {
    if (!sessions.stream.id) {
        showToast('没有可清空的对话记录', 'warning');
        return;
    }

    if (!confirm('确定要清空对话历史吗？')) {
        return;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/chat/clear`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                Id: sessions.stream.id
            })
        });

        const result = await response.json();

        if (result.code === 200) {
            elements.streamMessages.innerHTML = `
                <div class="empty-state">
                    <i class="bi bi-chat-square-dots"></i>
                    <p class="mb-0">开始流式对话吧！</p>
                </div>
            `;
            sessions.stream.id = '';
            sessions.stream.messageCount = 0;
            showToast('对话历史已清空', 'success');
        } else {
            showToast(result.message || '清空失败', 'error');
        }
    } catch (error) {
        console.error('清空会话失败:', error);
        showToast('网络错误', 'error');
    }
}

// ==================== AI运维功能 ====================

function initAiOpsPanel() {
    elements.startAiOpsBtn.addEventListener('click', startAiOpsAnalysis);
}

// 开始AI运维分析
async function startAiOpsAnalysis() {
    elements.aiOpsOutput.innerHTML = '<div class="text-center p-5"><div class="spinner-border text-primary mb-3" style="width: 3rem; height: 3rem;"></div><p class="mb-0">正在分析中...</p></div>';
    elements.startAiOpsBtn.disabled = true;
    let isCompleted = false;

    try {
        const response = await fetch(`${API_BASE_URL}/ai_ops`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'text/event-stream',
                'Cache-Control': 'no-cache'
            }
        });

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder('utf-8');
        let buffer = '';
        let fullOutput = '';

        // 处理单个事件的函数
        function processEvent(dataString) {
            if (!dataString.trim()) return;

            try {
                const data = JSON.parse(dataString.trim());
                console.log('收到AI运维事件:', data);

                if (data.type === 'content') {
                    fullOutput += data.data || '';
                    elements.aiOpsOutput.innerHTML = escapeHtml(fullOutput).replace(/\n/g, '<br>') + '<span class="typing-indicator"></span>';
                    elements.aiOpsOutput.scrollTop = elements.aiOpsOutput.scrollHeight;
                } else if (data.type === 'error') {
                    elements.aiOpsOutput.innerHTML = `<div class="text-danger">错误: ${data.data}</div>`;
                    isCompleted = true;
                } else if (data.type === 'done') {
                    elements.aiOpsOutput.innerHTML = escapeHtml(fullOutput).replace(/\n/g, '<br>');
                    showToast('AI运维分析完成', 'success');
                    isCompleted = true;
                }
            } catch (e) {
                console.error('解析SSE数据失败:', e, '原始数据:', dataString);
            }
        }

        while (true) {
            const { done, value } = await reader.read();
            if (done) {
                console.log('AI运维流读取完成');
                break;
            }

            buffer += decoder.decode(value, { stream: true });

            // 按 \n\n 或 \r\n\r\n 分割事件
            const events = buffer.split(/\n\n|\r\n\r\n/);
            buffer = events.pop() || '';

            for (const event of events) {
                const dataLines = event.split('\n');
                for (const line of dataLines) {
                    if (line.trim().startsWith('data:')) {
                        const dataContent = line.slice(5).trim();
                        if (dataContent) {
                            processEvent(dataContent);
                            if (isCompleted) return;
                        }
                    }
                }
            }
        }

        // 处理缓冲区中剩余的数据
        if (buffer.trim()) {
            const dataLines = buffer.split('\n');
            for (const line of dataLines) {
                if (line.trim().startsWith('data:')) {
                    const dataContent = line.slice(5).trim();
                    if (dataContent) {
                        processEvent(dataContent);
                    }
                }
            }
        }

    } catch (error) {
        console.error('AI运维分析失败:', error);
        elements.aiOpsOutput.innerHTML = `<div class="text-danger">分析失败: ${error.message}</div>`;
        showToast('AI运维分析失败', 'error');
    } finally {
        console.log('AI运维清理资源，重新启用按钮');
        elements.startAiOpsBtn.disabled = false;
    }
}

// ==================== 文件上传功能 ====================

function initUploadPanel() {
    // 上传按钮点击
    elements.uploadBtn.addEventListener('click', () => {
        const modal = new bootstrap.Modal(elements.uploadModal);
        modal.show();
    });

    // 模态框关闭时重置
    elements.uploadModal.addEventListener('hidden.bs.modal', () => {
        clearFileSelection();
        elements.uploadProgress.classList.add('d-none');
    });

    // 点击上传区域选择文件
    elements.uploadArea.addEventListener('click', () => {
        elements.fileInput.click();
    });

    // 文件选择变化
    elements.fileInput.addEventListener('change', (e) => {
        const file = e.target.files[0];
        if (file) {
            handleFileSelect(file);
        }
    });

    // 文件拖拽功能
    elements.uploadArea.addEventListener('dragover', (e) => {
        e.preventDefault();
        elements.uploadArea.classList.add('dragover');
    });

    elements.uploadArea.addEventListener('dragleave', () => {
        elements.uploadArea.classList.remove('dragover');
    });

    elements.uploadArea.addEventListener('drop', (e) => {
        e.preventDefault();
        elements.uploadArea.classList.remove('dragover');

        const files = e.dataTransfer.files;
        if (files.length > 0) {
            handleFileSelect(files[0]);
        }
    });

    // 移除文件
    elements.removeFileBtn.addEventListener('click', () => {
        clearFileSelection();
    });

    // 确认上传
    elements.confirmUploadBtn.addEventListener('click', uploadFile);
}

// 处理文件选择
function handleFileSelect(file) {
    // 检查文件类型
    const allowedExtensions = ['.txt', '.md'];
    const fileExtension = file.name.substring(file.name.lastIndexOf('.')).toLowerCase();
    if (!allowedExtensions.includes(fileExtension)) {
        showToast('不支持的文件格式，仅支持 .txt 和 .md 文件', 'error');
        clearFileSelection();
        return;
    }

    // 检查文件大小（限制为10MB）
    const maxSize = 10 * 1024 * 1024;
    if (file.size > maxSize) {
        showToast('文件大小不能超过 10MB', 'error');
        clearFileSelection();
        return;
    }

    // 显示文件信息
    elements.fileName.textContent = file.name;
    elements.fileInfo.classList.remove('d-none');
    elements.confirmUploadBtn.disabled = false;

    // 保存文件引用
    window.selectedFile = file;
}

// 清除文件选择
function clearFileSelection() {
    elements.fileInput.value = '';
    elements.fileInfo.classList.add('d-none');
    elements.confirmUploadBtn.disabled = true;
    window.selectedFile = null;
}

// 上传文件
async function uploadFile() {
    if (!window.selectedFile) {
        showToast('请先选择文件', 'warning');
        return;
    }

    const formData = new FormData();
    formData.append('file', window.selectedFile);

    // 显示进度条
    elements.uploadProgress.classList.remove('d-none');
    elements.progressBar.style.width = '0%';
    elements.progressText.textContent = '0%';
    elements.confirmUploadBtn.disabled = true;
    elements.confirmUploadBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>上传中...';

    try {
        const xhr = new XMLHttpRequest();

        // 上传进度
        xhr.upload.addEventListener('progress', (e) => {
            if (e.lengthComputable) {
                const percentComplete = Math.round((e.loaded / e.total) * 100);
                elements.progressBar.style.width = percentComplete + '%';
                elements.progressText.textContent = percentComplete + '%';
            }
        });

        // 完成处理
        xhr.addEventListener('load', () => {
            if (xhr.status === 200) {
                const response = JSON.parse(xhr.responseText);
                if (response.code === 200) {
                    showToast(`文件上传成功！`, 'success');
                    // 关闭模态框
                    const modal = bootstrap.Modal.getInstance(elements.uploadModal);
                    modal.hide();
                    // 重置表单
                    clearFileSelection();
                    elements.uploadProgress.classList.add('d-none');
                    elements.confirmUploadBtn.innerHTML = '<i class="bi bi-upload me-1"></i>上传';
                } else {
                    showToast(response.message || '上传失败', 'error');
                    resetUploadButton();
                }
            } else {
                showToast('上传失败：服务器错误', 'error');
                resetUploadButton();
            }
        });

        // 错误处理
        xhr.addEventListener('error', () => {
            showToast('上传失败：网络错误', 'error');
            resetUploadButton();
        });

        // 发送请求
        xhr.open('POST', `${API_BASE_URL}/upload`);
        xhr.send(formData);

    } catch (error) {
        console.error('上传失败:', error);
        showToast('上传失败：' + error.message, 'error');
        resetUploadButton();
    }
}

// 重置上传按钮状态
function resetUploadButton() {
    elements.confirmUploadBtn.disabled = false;
    elements.confirmUploadBtn.innerHTML = '<i class="bi bi-upload me-1"></i>上传';
    elements.uploadProgress.classList.add('d-none');
}

// ==================== 工具函数 ====================

// 生成会话ID
function generateSessionId() {
    return 'session_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
}

// 时间格式化
function formatTime(date) {
    return date.toLocaleTimeString('zh-CN', {
        hour: '2-digit',
        minute: '2-digit'
    });
}

// HTML转义
function escapeHtml(text) {
    const map = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#039;'
    };
    return text.replace(/[&<>"']/g, m => map[m]);
}

// Toast提示
function showToast(message, type = 'info') {
    let toastContainer = document.getElementById('toast-container');
    if (!toastContainer) {
        toastContainer = document.createElement('div');
        toastContainer.id = 'toast-container';
        toastContainer.className = 'position-fixed top-0 end-0 p-3';
        toastContainer.style.zIndex = '9999';
        document.body.appendChild(toastContainer);
    }

    const toastId = 'toast-' + Date.now();
    const bgClass = {
        success: 'bg-success',
        error: 'bg-danger',
        warning: 'bg-warning',
        info: 'bg-info'
    }[type] || 'bg-info';

    const toastHTML = `
        <div id="${toastId}" class="toast align-items-center text-white ${bgClass}" role="alert">
            <div class="d-flex">
                <div class="toast-body">
                    ${message}
                </div>
                <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>
            </div>
        </div>
    `;

    toastContainer.insertAdjacentHTML('beforeend', toastHTML);

    const toastElement = document.getElementById(toastId);
    const toast = new bootstrap.Toast(toastElement);
    toast.show();

    toastElement.addEventListener('hidden.bs.toast', () => {
        toastElement.remove();
    });
}
