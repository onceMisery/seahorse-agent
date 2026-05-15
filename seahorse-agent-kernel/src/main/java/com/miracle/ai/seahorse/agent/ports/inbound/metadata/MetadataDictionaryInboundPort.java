package com.miracle.ai.seahorse.agent.ports.inbound.metadata;

import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryItemPayload;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryItemRecord;

import java.util.List;

/**
 * 元数据标准化字典管理入站端口。
 */
public interface MetadataDictionaryInboundPort {

    List<MetadataDictionaryItemRecord> listItems(String tenantId, String dictionaryCode, boolean includeDisabled);

    MetadataDictionaryItemRecord createItem(MetadataDictionaryItemPayload payload);

    MetadataDictionaryItemRecord updateItem(String itemId, MetadataDictionaryItemPayload payload);

    boolean deleteItem(String itemId);
}
