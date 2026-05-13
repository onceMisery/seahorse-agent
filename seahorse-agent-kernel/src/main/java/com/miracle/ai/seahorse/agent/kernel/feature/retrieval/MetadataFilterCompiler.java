package com.miracle.ai.seahorse.agent.kernel.feature.retrieval;

import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataSchema;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.CompiledMetadataFilter;

/**
 * 元数据过滤编译器。
 * <p>
 * 职责是基于 Schema 拒绝未知字段和非法操作符，并输出后端无关 AST。
 */
public interface MetadataFilterCompiler {

    CompiledMetadataFilter compile(RetrievalFilter filter, MetadataSchema schema);
}
