# RAG 检索增强生成技术

## 什么是 RAG

RAG（Retrieval-Augmented Generation，检索增强生成）是将**信息检索**与**文本生成**结合的技术。它解决了 LLM 的两大局限：

1. **知识截止日期**：LLM 训练数据有截止时间，不知道最新信息
2. **私有知识缺失**：LLM 不知道企业内部文档、个人笔记等私有内容

RAG 的核心流程：**文档向量化 → 问题检索 → 上下文增强 → LLM 生成**

## RAG 系统架构

```
┌─────────────────────────────────────────────┐
│              离线阶段（文档消化）              │
│                                               │
│  Markdown/PDF/TXT 文档                        │
│       │                                       │
│       ▼  DocumentReader（文档解析）           │
│       │  提取文本 + 元数据                     │
│       │                                       │
│       ▼  DocumentSplitter（文档切分）         │
│       │  TokenTextSplitter: 200 tokens/块     │
│       │                                       │
│       ▼  KeywordEnricher（关键词增强）        │
│       │  LLM 提取 5 个关键词                   │
│       │                                       │
│       ▼  EmbeddingModel（向量化）             │
│       │  1536 维向量                           │
│       │                                       │
│       ▼  VectorStore（向量存储）              │
│       │  SimpleVectorStore / PgVectorStore    │
│       │  COSINE_DISTANCE 相似度搜索           │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│              在线阶段（用户提问）              │
│                                               │
│  用户提问："什么是 ReAct 模式？"               │
│       │                                       │
│       ▼  QueryRewriter（查询重写）            │
│       │  LLM 改写为更精准的检索 query          │
│       │                                       │
│       ▼  VectorStore.similaritySearch()       │
│       │  返回 top-K 最相关文档片段             │
│       │                                       │
│       ▼  QuestionAnswerAdvisor                │
│       │  将文档片段拼入 System Prompt          │
│       │  "Based on the following documents:   │
│       │   {doc1} {doc2} {doc3}                │
│       │   Answer: {user question}"             │
│       │                                       │
│       ▼  LLM 生成回答                          │
│       │  基于检索到的文档内容作答               │
│       │  引用出处、诚实说明不知道              │
└─────────────────────────────────────────────┘
```

## 文档切分策略

文档切分是 RAG 最关键的基础步骤，直接影响检索质量。

### TokenTextSplitter 参数

```java
new TokenTextSplitter(
    200,   // defaultChunkSize: 每块最多 200 token
    100,   // minChunkSizeChars: 最小块字符数
    10,    // minChunkLengthToEmbed: 小于此长度不嵌入
    5000,  // maxNumChunks: 最大块数
    true   // keepSeparator: 保留分隔符
);
```

### 块重叠 (Chunk Overlap)

块与块之间有 100 token 重叠，防止关键信息被切在边界上。

```
...前一块末尾 100 token... | ...新块正文 200 token... | ...后一块开头 100 token...
                            ↑
                    这 100 token 在上一块和下一块都出现
```

## 查询重写 (Query Rewriting)

用户口语化问题往往不适合直接做向量检索。查询重写让 LLM 把问题改写为更精准的检索 query：

- 用户输入："怎么做 Agent 开发"
- 重写后："AI Agent 开发入门教程 架构设计 工具调用 Spring AI"

实现（QueryRewriter.java）：
```java
QueryTransformer queryTransformer = RewriteQueryTransformer.builder()
    .chatClientBuilder(ChatClient.builder(chatModel))
    .build();
Query transformedQuery = queryTransformer.transform(new Query(prompt));
```

## 关键词增强

每个文档块在向量化之前，先调用 LLM 提取关键词，补充到文档的 metadata 中。这提供了**关键词匹配**的额外信号，在语义搜索效果不佳时可以兜底。

```java
KeywordMetadataEnricher enricher = new KeywordMetadataEnricher(chatModel, 5);
List<Document> enriched = enricher.apply(documents);
// document.metadata.keywords = ["Agent","ReAct","工具","规划","Spring AI"]
```

## 向量存储方案对比

| 特性 | SimpleVectorStore | PgVectorStore |
|------|------------------|---------------|
| 存储位置 | 内存 | PostgreSQL |
| 持久化 | 否（重启丢失） | 是 |
| 性能 | 高（内存访问） | 中（数据库查询） |
| 适用场景 | 开发调试 | 生产环境 |
| 配置复杂度 | 低 | 中（需要 PostgreSQL） |

配置示例（application.yml）：
```yaml
spring.ai.vectorstore.pgvector:
  index-type: HNSW           # 索引类型
  dimensions: 1536           # 向量维度
  distance-type: COSINE_DISTANCE  # 相似度计算方式
```

## 高级 RAG 技术

### 元数据过滤

在检索时按文档元数据过滤，只搜索特定类别：

```java
Filter.Expression filter = new FilterExpressionBuilder()
    .eq("status", "agent")   // 只搜索 status=agent 的文档
    .build();
DocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
    .vectorStore(vectorStore)
    .filterExpression(filter)
    .similarityThreshold(0.5)  // 相似度阈值
    .topK(3)                   // 返回 top 3
    .build();
```

### 上下文增强器 (ContextualQueryAugmenter)

当检索不到相关文档时，可以配置"空上下文模板"：

```java
ContextualQueryAugmenter.builder()
    .allowEmptyContext(false)
    .emptyContextPromptTemplate(new PromptTemplate("""
        抱歉，我只能回答知识库内已有的问题，其他问题暂时无法帮到您。
        """))
    .build();
```

### 多查询扩展 (Multi-Query)

从多个角度改写用户问题，分别检索后合并结果，提高召回率。

## RAG 评估指标

- **召回率 (Recall)**：检索到的相关文档数 / 总相关文档数
- **精确率 (Precision)**：检索到的相关文档数 / 检索到的总文档数
- **忠实度 (Faithfulness)**：LLM 回答是否基于检索到的文档，而非编造
- **相关度 (Relevance)**：LLM 回答是否与用户问题相关
