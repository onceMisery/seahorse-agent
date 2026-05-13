package com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter;

/**
 * 后端无关的元数据过滤 AST。
 * <p>
 * 适配器只能翻译该 AST，不能直接解析用户传入的原始 Map。
 */
public sealed interface MetadataFilterExpr permits FieldEq, FieldIn, FieldRange, FieldContains, FieldExists, FilterAnd {
}
