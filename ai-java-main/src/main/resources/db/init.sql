-- 企业级AI知识库RAG问答与自动化评测平台 - 数据库初始化脚本
-- 数据库: qa_platform (docker-compose 自动创建)

-- 1. 提示词模板版本库
CREATE TABLE IF NOT EXISTS prompt_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    scene VARCHAR(100) NOT NULL COMMENT '场景名称（如：客服问答、知识检索、代码生成）',
    system_prompt TEXT NOT NULL COMMENT '系统提示词',
    user_template TEXT NOT NULL COMMENT '用户提示词模板（支持占位符 {{variable}}）',
    few_shot_examples JSON NULL COMMENT 'Few-Shot示例 [{"input":"...","output":"..."}]',
    temperature DOUBLE DEFAULT 0.7 COMMENT '模型温度参数 0.0-2.0',
    top_p DOUBLE DEFAULT 1.0 COMMENT '模型top_p参数',
    max_tokens INT DEFAULT 2048 COMMENT '最大生成Token数',
    version INT NOT NULL DEFAULT 1 COMMENT '版本号（同一场景递增）',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE-启用, ARCHIVED-归档, DRAFT-草稿',
    remark VARCHAR(500) DEFAULT '' COMMENT '版本备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_scene_version (scene, version),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提示词模板版本库';

-- 2. 上传文档记录
CREATE TABLE IF NOT EXISTS rag_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_type VARCHAR(20) NOT NULL COMMENT '文件类型: PDF, MD, TXT',
    file_size BIGINT DEFAULT 0 COMMENT '文件大小(bytes)',
    file_path VARCHAR(500) NOT NULL COMMENT '文件存储路径',
    chunk_size INT NOT NULL COMMENT '分块大小',
    chunk_overlap INT DEFAULT 0 COMMENT '分块重叠大小',
    chunk_count INT DEFAULT 0 COMMENT '分块总数',
    status VARCHAR(20) DEFAULT 'PROCESSING' COMMENT '处理状态: PROCESSING, COMPLETED, FAILED',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG文档上传记录';

-- 3. 向量分块记录
CREATE TABLE IF NOT EXISTS rag_chunk (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    document_id BIGINT NOT NULL COMMENT '关联文档ID',
    chunk_index INT NOT NULL COMMENT '分块序号（从0开始）',
    chunk_text TEXT NOT NULL COMMENT '分块文本内容',
    chunk_embedding_id VARCHAR(200) COMMENT 'ChromaDB中的向量ID（关联向量库）',
    char_count INT DEFAULT 0 COMMENT '字符数',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_document_id (document_id),
    INDEX idx_embedding_id (chunk_embedding_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG向量分块记录';

-- 4. 标准评测问题集
CREATE TABLE IF NOT EXISTS qa_test_set (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    set_name VARCHAR(200) NOT NULL COMMENT '评测集名称',
    question TEXT NOT NULL COMMENT '评测问题',
    reference_answer TEXT COMMENT '参考答案（用于LLM-as-Judge对比）',
    expected_keywords JSON COMMENT '期望关键词列表 ["关键词1","关键词2"]',
    category VARCHAR(100) COMMENT '问题分类',
    difficulty VARCHAR(20) DEFAULT 'MEDIUM' COMMENT '难度: EASY, MEDIUM, HARD',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE, ARCHIVED',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_set_name (set_name),
    INDEX idx_difficulty (difficulty)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标准评测问题集';

-- 5. 自动化评测结果记录
CREATE TABLE IF NOT EXISTS qa_evaluation_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    task_id VARCHAR(64) NOT NULL COMMENT '评测任务ID（批量评测关联）',
    test_question_id BIGINT NOT NULL COMMENT '关联评测问题ID',
    prompt_template_id BIGINT COMMENT '使用的提示词模板ID（消融实验用）',
    rag_config_snapshot JSON COMMENT 'RAG配置快照 {chunkSize, topK, overlap, ...}',
    model_response TEXT COMMENT '模型生成答案',
    retrieval_precision DOUBLE COMMENT '检索精准度（客观指标，Java计算）',
    context_recall DOUBLE COMMENT '上下文召回率（客观指标，Java计算）',
    answer_relevance DOUBLE COMMENT '答案相关性（LLM-as-Judge打分 1-5）',
    context_faithfulness DOUBLE COMMENT '上下文忠实度（LLM-as-Judge打分 1-5）',
    hallucination_score DOUBLE COMMENT '幻觉风险评分（LLM-as-Judge打分 1-5，越低越好）',
    judge_raw_response TEXT COMMENT 'LLM-as-Judge原始返回（完整JSON，用于偏差分析）',
    latency_ms BIGINT COMMENT '请求耗时(ms)',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态: PENDING, RUNNING, COMPLETED, FAILED',
    error_msg TEXT COMMENT '错误信息',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_task_id (task_id),
    INDEX idx_test_question_id (test_question_id),
    INDEX idx_prompt_template_id (prompt_template_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自动化评测结果记录';

-- 6. 消融实验任务
CREATE TABLE IF NOT EXISTS ablation_experiment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    experiment_name VARCHAR(200) NOT NULL COMMENT '实验名称',
    test_set_name VARCHAR(200) NOT NULL COMMENT '使用的评测集名称',
    base_config JSON NOT NULL COMMENT '基准配置 {promptId, chunkSize, topK, modelName, ...}',
    variable_configs JSON NOT NULL COMMENT '变量配置列表 [{"variable":"chunkSize","values":[256,512,1024]}, ...]',
    total_tasks INT DEFAULT 0 COMMENT '总评测任务数',
    completed_tasks INT DEFAULT 0 COMMENT '已完成任务数',
    summary_report TEXT COMMENT '汇总报告JSON（所有实验对比结果）',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态: PENDING, RUNNING, COMPLETED, FAILED',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消融实验任务';

-- 7. 对话日志
CREATE TABLE IF NOT EXISTS chat_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    session_id VARCHAR(64) NOT NULL COMMENT '会话ID',
    prompt_template_id BIGINT COMMENT '使用的提示词模板ID',
    rag_document_id BIGINT COMMENT '关联的RAG文档ID（如有）',
    user_question TEXT NOT NULL COMMENT '用户问题',
    retrieved_chunks TEXT COMMENT '召回的文本块（JSON数组）',
    full_prompt TEXT COMMENT '拼接后的完整Prompt（含上下文）',
    model_response TEXT COMMENT '模型生成的回答',
    total_tokens INT DEFAULT 0 COMMENT '消耗总Token数',
    latency_ms BIGINT COMMENT '请求耗时(ms)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '对话时间',
    INDEX idx_session_id (session_id),
    INDEX idx_create_time (create_time),
    INDEX idx_prompt_template_id (prompt_template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话日志';

-- 8. Python微调任务记录
CREATE TABLE IF NOT EXISTS train_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    task_name VARCHAR(200) NOT NULL COMMENT '微调任务名称',
    model_base VARCHAR(100) NOT NULL COMMENT '基座模型（如 Qwen2-7B-Instruct）',
    dataset_name VARCHAR(200) NOT NULL COMMENT '数据集名称',
    dataset_path VARCHAR(500) COMMENT '数据集文件路径',
    lora_rank INT DEFAULT 64 COMMENT 'LoRA rank',
    lora_alpha INT DEFAULT 16 COMMENT 'LoRA alpha',
    learning_rate DOUBLE DEFAULT 2e-4 COMMENT '学习率',
    num_epochs INT DEFAULT 3 COMMENT '训练轮数',
    batch_size INT DEFAULT 4 COMMENT 'batch size',
    python_service_url VARCHAR(300) COMMENT 'Python微调服务URL',
    python_task_id VARCHAR(100) COMMENT 'Python端任务ID',
    progress INT DEFAULT 0 COMMENT '训练进度 0-100',
    lora_weight_path VARCHAR(500) COMMENT 'LoRA权重输出路径',
    metrics JSON COMMENT '训练指标JSON {loss, eval_loss, ...}',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态: PENDING, TRAINING, COMPLETED, FAILED',
    error_msg TEXT COMMENT '错误信息',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Python微调任务记录';
