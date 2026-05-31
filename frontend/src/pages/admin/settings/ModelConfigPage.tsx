import { useEffect, useState } from "react";
import { toast } from "sonner";
import { Pencil, Save, X, Eye, EyeOff } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { getErrorMessage } from "@/utils/error";
import {
  getAiModelConfigs,
  updateAiModelConfig,
  type AiModelConfigItem,
} from "@/services/aiConfigService";

interface ConfigFormData {
  baseUrl: string;
  apiKey: string;
  chatModel: string;
  embeddingModel: string;
  rerankModel: string;
}

export function ModelConfigPage() {
  const [configs, setConfigs] = useState<AiModelConfigItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [showApiKey, setShowApiKey] = useState(false);
  const [formData, setFormData] = useState<ConfigFormData>({
    baseUrl: "",
    apiKey: "",
    chatModel: "",
    embeddingModel: "",
    rerankModel: "",
  });
  const [originalData, setOriginalData] = useState<ConfigFormData>({
    baseUrl: "",
    apiKey: "",
    chatModel: "",
    embeddingModel: "",
    rerankModel: "",
  });

  const loadConfigs = async () => {
    try {
      setLoading(true);
      const data = await getAiModelConfigs();
      setConfigs(data);

      // 映射配置到表单
      const configMap = data.reduce((acc, item) => {
        acc[item.configKey] = item.configValue;
        return acc;
      }, {} as Record<string, string>);

      const newFormData = {
        baseUrl: configMap["ai.base.url"] || "",
        apiKey: "",
        chatModel: configMap["ai.chat.model"] || "",
        embeddingModel: configMap["ai.embedding.model"] || "",
        rerankModel: configMap["ai.rerank.model"] || "",
      };

      setFormData(newFormData);
      setOriginalData(newFormData);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载配置失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadConfigs();
  }, []);

  const handleSave = async () => {
    try {
      setSaving(true);

      const updates: Array<{ key: string; value: string }> = [];

      if (formData.baseUrl !== originalData.baseUrl) {
        updates.push({ key: "ai.base.url", value: formData.baseUrl });
      }
      if (formData.apiKey !== originalData.apiKey) {
        updates.push({ key: "ai.api.key", value: formData.apiKey });
      }
      if (formData.chatModel !== originalData.chatModel) {
        updates.push({ key: "ai.chat.model", value: formData.chatModel });
      }
      if (formData.embeddingModel !== originalData.embeddingModel) {
        updates.push({ key: "ai.embedding.model", value: formData.embeddingModel });
      }
      if (formData.rerankModel !== originalData.rerankModel) {
        updates.push({ key: "ai.rerank.model", value: formData.rerankModel });
      }

      if (updates.length === 0) {
        toast.info("没有配置变更");
        setEditing(false);
        return;
      }

      // 批量更新
      await Promise.all(
        updates.map((update) => updateAiModelConfig(update.key, update.value))
      );

      toast.success("配置已保存并实时生效");
      setEditing(false);
      await loadConfigs();
    } catch (error) {
      toast.error(getErrorMessage(error, "保存配置失败"));
    } finally {
      setSaving(false);
    }
  };

  const handleCancel = () => {
    setEditing(false);
    setFormData(originalData);
    setShowApiKey(false);
  };

  if (loading) {
    return (
      <div className="admin-page">
        <div className="text-sm text-muted-foreground">加载中...</div>
      </div>
    );
  }

  const apiKeyConfig = configs.find((c) => c.configKey === "ai.api.key");
  const displayApiKey = editing && showApiKey ? formData.apiKey : (apiKeyConfig?.displayValue || "********");

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">大模型配置</h1>
          <p className="admin-page-subtitle">配置 AI 模型服务提供商和模型，实时生效</p>
        </div>
        <div className="flex gap-2">
          {!editing ? (
            <Button onClick={() => setEditing(true)} variant="outline">
              <Pencil className="mr-2 h-4 w-4" />
              编辑配置
            </Button>
          ) : (
            <>
              <Button onClick={handleSave} variant="default" disabled={saving}>
                <Save className="mr-2 h-4 w-4" />
                {saving ? "保存中..." : "保存"}
              </Button>
              <Button onClick={handleCancel} variant="outline" disabled={saving}>
                <X className="mr-2 h-4 w-4" />
                取消
              </Button>
            </>
          )}
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>模型服务配置</CardTitle>
          <CardDescription>
            配置 OpenAI 兼容的 API 服务地址和密钥
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-4 md:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="baseUrl">API 基础地址</Label>
              <Input
                id="baseUrl"
                value={formData.baseUrl}
                onChange={(e) => setFormData({ ...formData, baseUrl: e.target.value })}
                disabled={!editing}
                placeholder="https://api.siliconflow.cn/v1"
              />
              <p className="text-xs text-muted-foreground">
                OpenAI 兼容的 API 端点地址
              </p>
            </div>

            <div className="space-y-2">
              <Label htmlFor="apiKey">API 密钥</Label>
              <div className="relative">
                <Input
                  id="apiKey"
                  type={editing && showApiKey ? "text" : "password"}
                  value={editing ? formData.apiKey : displayApiKey}
                  onChange={(e) => setFormData({ ...formData, apiKey: e.target.value })}
                  disabled={!editing}
                  placeholder="sk-..."
                  className="pr-10"
                />
                {editing && (
                  <button
                    type="button"
                    onClick={() => setShowApiKey(!showApiKey)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                  >
                    {showApiKey ? (
                      <EyeOff className="h-4 w-4" />
                    ) : (
                      <Eye className="h-4 w-4" />
                    )}
                  </button>
                )}
              </div>
              <p className="text-xs text-muted-foreground">
                用于身份验证的 API 密钥（加密存储）
              </p>
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>模型选择</CardTitle>
          <CardDescription>
            配置对话、向量化和重排序模型
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="chatModel">对话模型</Label>
            <Input
              id="chatModel"
              value={formData.chatModel}
              onChange={(e) => setFormData({ ...formData, chatModel: e.target.value })}
              disabled={!editing}
              placeholder="deepseek-ai/DeepSeek-V3.2"
            />
            <p className="text-xs text-muted-foreground">
              用于对话生成的主模型，例如：deepseek-ai/DeepSeek-V3.2, gpt-4o
            </p>
          </div>

          <div className="space-y-2">
            <Label htmlFor="embeddingModel">向量化模型</Label>
            <Input
              id="embeddingModel"
              value={formData.embeddingModel}
              onChange={(e) => setFormData({ ...formData, embeddingModel: e.target.value })}
              disabled={!editing}
              placeholder="BAAI/bge-m3"
            />
            <p className="text-xs text-muted-foreground">
              用于文本向量化的模型，例如：BAAI/bge-m3, text-embedding-3-large
            </p>
          </div>

          <div className="space-y-2">
            <Label htmlFor="rerankModel">重排序模型</Label>
            <Input
              id="rerankModel"
              value={formData.rerankModel}
              onChange={(e) => setFormData({ ...formData, rerankModel: e.target.value })}
              disabled={!editing}
              placeholder="Qwen/Qwen3-Reranker-8B"
            />
            <p className="text-xs text-muted-foreground">
              用于检索结果重排序的模型，例如：Qwen/Qwen3-Reranker-8B
            </p>
          </div>
        </CardContent>
      </Card>

      <Card className="border-green-200 bg-green-50/50">
        <CardHeader>
          <CardTitle className="text-green-900">配置说明</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2 text-sm text-green-800">
          <p>
            <strong>✅ 实时生效：</strong>配置保存后立即生效，无需重启后端服务。
          </p>
          <p>
            <strong>🔒 安全存储：</strong>API 密钥使用 AES 加密存储在数据库中，前端显示时自动脱敏。
          </p>
          <p>
            <strong>👤 权限控制：</strong>仅管理员可以编辑配置，所有变更记录操作人和时间。
          </p>
          <p>
            <strong>📝 推荐配置：</strong>
          </p>
          <ul className="ml-4 list-disc space-y-0.5">
            <li>SiliconFlow：性价比高，国内访问快</li>
            <li>对话模型：deepseek-ai/DeepSeek-V3.2</li>
            <li>向量化：BAAI/bge-m3（多语言支持）</li>
            <li>重排序：Qwen/Qwen3-Reranker-8B（中文优化）</li>
          </ul>
        </CardContent>
      </Card>
    </div>
  );
}
