package com.aiagent.tools.search;

import java.util.List;

/**
 * 搜索引擎抽象接口。
 * <p>各实现类负责对特定搜索引擎发起查询并返回标准化结果。
 */
@FunctionalInterface
public interface SearchEngine {

    /**
     * 执行搜索查询。
     *
     * @param query     搜索关键词
     * @param maxResults 最大返回条数
     * @return 标准化搜索结果列表（失败时返回空列表）
     */
    List<SearchResult> search(String query, int maxResults);
}
